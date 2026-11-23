package com.moldy.moldymarket.notification.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.notification.dto.NotificationPageResponse;
import com.moldy.moldymarket.notification.dto.NotificationRecord;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationService {
    private static final String PREFIX = "NOTIFICATION#";
    private final DynamoDbClient db;
    private final ObjectMapper json;
    private final String table;

    public NotificationService(DynamoDbClient db, ObjectMapper json,
            @Value("${aws.dynamodb.notifications-table:notifications}") String table) {
        this.db = db;
        this.json = json;
        this.table = table;
    }

    public NotificationPageResponse getPage(String userId, int limit, String token) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        var request = QueryRequest.builder().tableName(table).consistentRead(true).scanIndexForward(false)
                .limit(safeLimit).keyConditionExpression("PK = :pk AND begins_with(SK, :prefix)")
                .filterExpression("attribute_not_exists(deletedAt) AND (attribute_not_exists(expiresAt) OR expiresAt > :now)")
                .expressionAttributeValues(Map.of(":pk", string("USER#" + userId), ":prefix", string(PREFIX),
                        ":now", number(Instant.now().getEpochSecond())));
        if (token != null) request.exclusiveStartKey(cursor(userId, token));
        var result = db.query(request.build());
        String next = null;
        if (result.hasLastEvaluatedKey() && !result.lastEvaluatedKey().isEmpty()) {
            next = encode(json.writeValueAsString(Map.of("userId", userId, "key", result.lastEvaluatedKey().get("SK").s())));
        }
        return NotificationPageResponse.builder().items(result.items().stream().map(item -> record(userId, item)).toList())
                .limit(safeLimit).nextToken(next).hasMore(next != null).build();
    }

    public NotificationRecord getById(String userId, String notificationId) {
        var item = find(userId, decodeId(notificationId));
        if (item.containsKey("deletedAt")) throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND);
        return record(userId, item);
    }

    public void markAsRead(String userId, String notificationId) { update(userId, notificationId, Action.READ); }
    public void softDelete(String userId, String notificationId) { update(userId, notificationId, Action.DELETE); }
    public void restore(String userId, String notificationId) { update(userId, notificationId, Action.RESTORE); }

    private void update(String userId, String id, Action action) {
        String sk = decodeId(id);
        Instant now = Instant.now();
        var values = new HashMap<String, AttributeValue>();
        values.put(":epoch", number(now.getEpochSecond()));
        String expression;
        if (action == Action.READ) {
            expression = "SET isRead = :read, readAt = if_not_exists(readAt, :time)";
            values.put(":read", AttributeValue.builder().bool(true).build());
            values.put(":time", string(now.toString()));
        } else if (action == Action.DELETE) {
            expression = "SET deletedAt = :time";
            values.put(":time", string(now.toString()));
        } else expression = "REMOVE deletedAt";
        String deletionCondition = action == Action.RESTORE ? "attribute_exists(deletedAt)" : "attribute_not_exists(deletedAt)";
        try {
            db.updateItem(UpdateItemRequest.builder().tableName(table).key(key(userId, sk))
                    .updateExpression(expression).expressionAttributeValues(values)
                    .conditionExpression("attribute_exists(PK) AND attribute_exists(SK) AND " + deletionCondition
                            + " AND (attribute_not_exists(expiresAt) OR expiresAt > :epoch)").build());
        } catch (ConditionalCheckFailedException exception) {
            var item = find(userId, sk);
            if (action == Action.DELETE && item.containsKey("deletedAt"))
                throw new AppException(ErrorCode.NOTIFICATION_ALREADY_DELETED);
            if (action == Action.RESTORE && !item.containsKey("deletedAt"))
                throw new AppException(ErrorCode.NOTIFICATION_NOT_DELETED);
            throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    private Map<String, AttributeValue> find(String userId, String sk) {
        var item = db.getItem(GetItemRequest.builder().tableName(table).key(key(userId, sk)).consistentRead(true).build()).item();
        if (item.isEmpty() || (item.containsKey("expiresAt")
                && Long.parseLong(item.get("expiresAt").n()) <= Instant.now().getEpochSecond()))
            throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND);
        return item;
    }

    private NotificationRecord record(String userId, Map<String, AttributeValue> item) {
        return NotificationRecord.builder().id(encode(text(item, "SK"))).userId(userId)
                .type(text(item, "type")).title(text(item, "title")).message(text(item, "content"))
                .relatedEntityId(text(item, "referenceId")).relatedEntityType(text(item, "referenceType"))
                .read(item.containsKey("isRead") && Boolean.TRUE.equals(item.get("isRead").bool()))
                .deleted(item.containsKey("deletedAt")).createdAt(time(item, "createdAt")).readAt(time(item, "readAt")).build();
    }

    private Map<String, AttributeValue> cursor(String userId, String token) {
        String owner, sk;
        try {
            var node = json.readTree(decode(token, 4096));
            if (!node.path("userId").isTextual() || !node.path("key").isTextual()) throw new IllegalArgumentException();
            owner = node.path("userId").asText(); sk = node.path("key").asText();
            validateKey(sk);
        } catch (RuntimeException exception) { throw new AppException(ErrorCode.INVALID_PAGINATION_TOKEN); }
        if (!userId.equals(owner)) throw new AppException(ErrorCode.PAGINATION_TOKEN_USER_MISMATCH);
        return key(userId, sk);
    }

    private static String decodeId(String id) {
        try { String sk = decode(id, 1600); validateKey(sk); return sk; }
        catch (RuntimeException exception) { throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND); }
    }
    private static void validateKey(String sk) {
        if (sk.length() > 1024 || !sk.startsWith(PREFIX)) throw new IllegalArgumentException();
        int separator = sk.indexOf('#', PREFIX.length());
        if (separator < 0 || separator == sk.length() - 1) throw new IllegalArgumentException();
        Instant.parse(sk.substring(PREFIX.length(), separator));
    }
    private static String decode(String value, int max) {
        if (value == null || value.isEmpty() || value.length() > max) throw new IllegalArgumentException();
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
    private static String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static Map<String, AttributeValue> key(String userId, String sk) { return Map.of("PK", string("USER#" + userId), "SK", string(sk)); }
    private static AttributeValue string(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue number(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
    private static String text(Map<String, AttributeValue> item, String name) { return item.containsKey(name) ? item.get(name).s() : null; }
    private static Instant time(Map<String, AttributeValue> item, String name) { String value = text(item, name); return value == null ? null : Instant.parse(value); }
    private enum Action { READ, DELETE, RESTORE }
}
