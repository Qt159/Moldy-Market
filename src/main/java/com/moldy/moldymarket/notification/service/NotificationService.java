package com.moldy.moldymarket.notification.service;

import com.moldy.moldymarket.notification.dto.response.NotificationPageResponse;
import com.moldy.moldymarket.notification.dto.response.NotificationRecord;
import com.moldy.moldymarket.notification.repository.NotificationRepository;
import com.moldy.moldymarket.notification.repository.NotificationRepository.PageResult;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public NotificationPageResponse getPage(String userId, int limit, String token) {
        String lastSortKey = decodeToken(token);

        PageResult result = repository.findPage(
                userId,
                limit,
                lastSortKey);

        List<NotificationRecord> records = result.items()
                .stream()
                .map(this::toRecord)
                .toList();

        return new NotificationPageResponse(
                records,
                encodeToken(result.nextToken()),
                limit,
                result.hasMore());
    }

    public NotificationRecord getById(String userId, String notificationId) {
        Map<String, AttributeValue> item =
                repository.findById(userId, notificationId);

        if (item == null || isDeleted(item)) {
            throw new NoSuchElementException("Notification not found");
        }

        return toRecord(item);
    }

    public void markAsRead(String userId, String notificationId) {
        repository.updateReadStatus(userId, notificationId, true);
    }

    public void softDelete(String userId, String notificationId) {
        repository.updateDeletedStatus(userId, notificationId, true);
    }

    public void restore(String userId, String notificationId) {
        repository.updateDeletedStatus(userId, notificationId, false);
    }

    private NotificationRecord toRecord(Map<String, AttributeValue> item) {
        return new NotificationRecord(
                value(item, "notificationId"),
                value(item, "userId"),
                value(item, "type"),
                value(item, "title"),
                value(item, "content"),
                value(item, "referenceId"),
                value(item, "referenceType"),
                booleanValue(item, "isRead"),
                booleanValue(item, "isDeleted"),
                instantValue(item, "createdAt"),
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

    private Instant instantValue(
            Map<String, AttributeValue> item,
            String key
    ) {
        String value = value(item, key);
        return value == null ? null : Instant.parse(value);
    }

    private boolean isDeleted(Map<String, AttributeValue> item) {
        return booleanValue(item, "isDeleted");
    }

    private String encodeToken(String value) {
        if (value == null) {
            return null;
        }

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes());
    }

    private String decodeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(token)
            );

            if (!decoded.startsWith("NOTIFICATION#")) {
                throw new IllegalArgumentException("Invalid cursor");
            }

            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid notification cursor");
        }
    }
}