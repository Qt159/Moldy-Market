package com.moldy.moldymarket.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository thao tác với DynamoDB table: notifications
 *
 * Schema:
 *   PK = USER#{userId}
 *   SK = NOTIFICATION#{createdAt}#{notificationId}
 */
@Repository
public class NotificationRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public NotificationRepository(DynamoDbClient dynamoDbClient,
            @Value("${aws.dynamodb.notifications-table}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    // ── GET ALL (chưa xóa) ────────────────────────────────────────────
    public List<NotificationRecord> findAllByUserId(String userId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk")
                .filterExpression("attribute_not_exists(deletedAt)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("USER#" + userId)
                ))
                .scanIndexForward(false) // mới nhất trước
                .build();

        return dynamoDbClient.query(request)
                .items()
                .stream()
                .map(this::toRecord)
                .toList();
    }

    // ── GET BY ID ─────────────────────────────────────────────────────
    public Optional<NotificationRecord> findById(String userId, String notificationId) {
        // Cần query để tìm SK chứa notificationId (vì SK có timestamp)
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk AND contains(SK, :nid)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("USER#" + userId),
                        ":nid", AttributeValue.fromS(notificationId)
                ))
                .build();

        return dynamoDbClient.query(request)
                .items()
                .stream()
                .findFirst()
                .map(this::toRecord);
    }

    // ── MARK AS READ ──────────────────────────────────────────────────
    public void markAsRead(String userId, String sk) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", AttributeValue.fromS("USER#" + userId),
                        "SK", AttributeValue.fromS(sk)
                ))
                .updateExpression("SET isRead = :val")
                .expressionAttributeValues(Map.of(
                        ":val", AttributeValue.fromBool(true)
                ))
                .build();

        dynamoDbClient.updateItem(request);
    }

    // ── SOFT DELETE ───────────────────────────────────────────────────
    public void softDelete(String userId, String sk) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", AttributeValue.fromS("USER#" + userId),
                        "SK", AttributeValue.fromS(sk)
                ))
                .updateExpression("SET deletedAt = :ts")
                .expressionAttributeValues(Map.of(
                        ":ts", AttributeValue.fromS(Instant.now().toString())
                ))
                .build();

        dynamoDbClient.updateItem(request);
    }

    // ── RESTORE ───────────────────────────────────────────────────────
    public void restore(String userId, String sk) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", AttributeValue.fromS("USER#" + userId),
                        "SK", AttributeValue.fromS(sk)
                ))
                .updateExpression("REMOVE deletedAt")
                .build();

        dynamoDbClient.updateItem(request);
    }

    // ── Helper ────────────────────────────────────────────────────────
    private NotificationRecord toRecord(Map<String, AttributeValue> item) {
        return new NotificationRecord(
                getString(item, "notificationId"),
                getString(item, "userId"),
                getString(item, "type"),
                getString(item, "referenceType"),
                getString(item, "referenceId"),
                getString(item, "title"),
                getString(item, "content"),
                getBool(item, "isRead"),
                getString(item, "createdAt"),
                getString(item, "deletedAt")
        );
    }

    private String getString(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        return val != null ? val.s() : null;
    }

    private boolean getBool(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        return val != null && Boolean.TRUE.equals(val.bool());
    }
}
