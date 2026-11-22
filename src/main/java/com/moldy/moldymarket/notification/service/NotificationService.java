package com.moldy.moldymarket.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.notification.dto.response.NotificationPageResponse;
import com.moldy.moldymarket.notification.dto.response.NotificationRecord;
import com.moldy.moldymarket.notification.repository.NotificationRepository;
import com.moldy.moldymarket.notification.repository.NotificationRepository.PageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class NotificationService {

    private static final String NOTIFICATION_PREFIX = "NOTIFICATION#";
    private static final int MAX_CURSOR_LENGTH = 8192;
    private static final TypeReference<CursorPayload> CURSOR_TYPE = new TypeReference<>() {};

    private final NotificationRepository repository;
    private final ObjectMapper objectMapper;
    private final byte[] cursorSigningKey;

    public NotificationService(
            NotificationRepository repository,
            ObjectMapper objectMapper,
            @Value("${security.jwt.secret}") String cursorSigningKey) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.cursorSigningKey = cursorSigningKey.getBytes(StandardCharsets.UTF_8);
    }

    public NotificationPageResponse getPage(String userId, int limit, String token) {
        Map<String, AttributeValue> startKey = decodeToken(userId, token);
        PageResult result = repository.findPage(userId, limit, startKey);
        List<NotificationRecord> records = result.items().stream()
                .map(this::toRecord)
                .toList();

        return new NotificationPageResponse(
                records, encodeToken(userId, result.nextToken()), limit, result.hasMore());
    }

    public NotificationRecord getById(String userId, String notificationId) {
        Map<String, AttributeValue> item = repository.findById(userId, notificationId);
        if (item == null || isDeleted(item) || isExpired(item)) {
            throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        return toRecord(item);
    }

    public void markAsRead(String userId, String notificationId) {
        repository.updateReadStatus(userId, notificationId, true);
    }

    public void markAsUnread(String userId, String notificationId) {
        repository.updateReadStatus(userId, notificationId, false);
    }

    public void softDelete(String userId, String notificationId) {
        repository.updateDeletedStatus(userId, notificationId, true);
    }

    public void restore(String userId, String notificationId) {
        repository.updateDeletedStatus(userId, notificationId, false);
    }

    private String encodeToken(String userId, Map<String, AttributeValue> key) {
        if (key == null || key.isEmpty()) return null;
        validateKey(userId, key);
        Map<String, CursorAttribute> encodedKey = new TreeMap<>();
        key.forEach((name, value) -> encodedKey.put(name, encodeAttribute(value)));

        try {
            byte[] payloadBytes = objectMapper.writeValueAsBytes(
                    new CursorPayload(userId, encodedKey));
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadBytes);
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sign(payload));
            return payload + "." + signature;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode notification cursor", exception);
        }
    }

    private Map<String, AttributeValue> decodeToken(String userId, String token) {
        if (token == null || token.isBlank()) return null;
        if (token.length() > MAX_CURSOR_LENGTH) throw invalidCursor();

        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || !isBase64Url(parts[0]) || !isBase64Url(parts[1])) {
            throw invalidCursor();
        }

        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] payloadBytes = decoder.decode(parts[0]);
            byte[] providedSignature = decoder.decode(parts[1]);
            if (!MessageDigest.isEqual(sign(parts[0]), providedSignature)) {
                throw invalidCursor();
            }

            CursorPayload payload = objectMapper.readValue(payloadBytes, CURSOR_TYPE);
            if (payload == null || payload.userId() == null || !payload.userId().equals(userId)) {
                throw new AppException(ErrorCode.PAGINATION_TOKEN_USER_MISMATCH);
            }
            if (payload.lastEvaluatedKey() == null || payload.lastEvaluatedKey().isEmpty()) {
                throw invalidCursor();
            }

            Map<String, AttributeValue> key = new LinkedHashMap<>();
            payload.lastEvaluatedKey().forEach((name, value) -> {
                if (name == null || name.isBlank() || value == null) throw invalidCursor();
                key.put(name, decodeAttribute(value));
            });
            validateKey(userId, key);
            return Map.copyOf(key);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidCursor();
        }
    }

    private CursorAttribute encodeAttribute(AttributeValue value) {
        int scalarTypes = (value.s() != null ? 1 : 0)
                + (value.n() != null ? 1 : 0)
                + (value.b() != null ? 1 : 0);
        if (scalarTypes != 1) throw invalidCursor();
        if (value.s() != null) return new CursorAttribute("S", value.s());
        if (value.n() != null) return new CursorAttribute("N", value.n());
        return new CursorAttribute("B", Base64.getEncoder().encodeToString(value.b().asByteArray()));
    }

    private AttributeValue decodeAttribute(CursorAttribute value) {
        if (value.type() == null || value.value() == null) throw invalidCursor();
        return switch (value.type()) {
            case "S" -> AttributeValue.builder().s(value.value()).build();
            case "N" -> {
                try {
                    new java.math.BigDecimal(value.value());
                } catch (NumberFormatException exception) {
                    throw invalidCursor();
                }
                yield AttributeValue.builder().n(value.value()).build();
            }
            case "B" -> AttributeValue.builder().b(SdkBytes.fromByteArray(
                    Base64.getDecoder().decode(value.value()))).build();
            default -> throw invalidCursor();
        };
    }

    private void validateKey(String userId, Map<String, AttributeValue> key) {
        AttributeValue pk = key.get("PK");
        AttributeValue sk = key.get("SK");
        if (userId == null || userId.isBlank() || pk == null || pk.s() == null
                || !pk.s().equals("USER#" + userId)
                || sk == null || sk.s() == null || !sk.s().startsWith(NOTIFICATION_PREFIX)) {
            throw invalidCursor();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cursorSigningKey, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign notification cursor", exception);
        }
    }

    private boolean isBase64Url(String value) {
        return !value.isEmpty() && value.matches("[A-Za-z0-9_-]+");
    }

    private AppException invalidCursor() {
        return new AppException(ErrorCode.INVALID_PAGINATION_TOKEN);
    }

    private NotificationRecord toRecord(Map<String, AttributeValue> item) {
        return new NotificationRecord(
                value(item, "notificationId"), value(item, "userId"), value(item, "type"),
                value(item, "title"), value(item, "content"), value(item, "referenceId"),
                value(item, "referenceType"), booleanValue(item, "isRead"),
                booleanValue(item, "isDeleted"), instantValue(item, "createdAt"),
                instantValue(item, "readAt"));
    }

    private String value(Map<String, AttributeValue> item, String key) {
        AttributeValue attribute = item.get(key);
        return attribute == null ? null : attribute.s();
    }

    private boolean booleanValue(Map<String, AttributeValue> item, String key) {
        AttributeValue attribute = item.get(key);
        return attribute != null && Boolean.TRUE.equals(attribute.bool());
    }

    private Instant instantValue(Map<String, AttributeValue> item, String key) {
        String value = value(item, key);
        return value == null ? null : Instant.parse(value);
    }

    private boolean isDeleted(Map<String, AttributeValue> item) {
        return booleanValue(item, "isDeleted");
    }

    private boolean isExpired(Map<String, AttributeValue> item) {
        AttributeValue expiresAt = item.get("expiresAt");
        if (expiresAt == null || expiresAt.n() == null) return false;
        try {
            return Long.parseLong(expiresAt.n()) <= Instant.now().getEpochSecond();
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    public record CursorPayload(String userId, Map<String, CursorAttribute> lastEvaluatedKey) {}
    public record CursorAttribute(String type, String value) {}
}
