package com.moldy.moldymarket.notification.repository;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.notification.dto.NotificationRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class NotificationRepository {
    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public NotificationRepository(DynamoDbClient dynamoDb,
                                  @Value("${aws.dynamodb.notifications-table}") String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    public QueryResult findByUserId(String userId, int limit, Map<String, AttributeValue> startKey) {
        QueryRequest.Builder request = queryForUser()
                .filterExpression("attribute_not_exists(deletedAt)")
                .expressionAttributeValues(Map.of(":pk", partition(userId)))
                .scanIndexForward(false)
                .limit(limit);
        if (startKey != null) request.exclusiveStartKey(startKey);

        var response = dynamoDb.query(request.build());
        List<NotificationRecord> items = response.items().stream()
                .map(NotificationRepository::toResponse)
                .toList();
        return new QueryResult(items, response.lastEvaluatedKey());
    }

    public Optional<StoredNotification> findById(String userId, String notificationId) {
        QueryRequest request = queryForUser()
                .filterExpression("notificationId = :notificationId")
                .expressionAttributeValues(Map.of(
                        ":pk", partition(userId),
                        ":notificationId", AttributeValue.fromS(notificationId)))
                .build();
        return dynamoDb.queryPaginator(request).items().stream()
                .findFirst()
                .map(item -> new StoredNotification(text(item, "SK"), toResponse(item)));
    }

    public void markAsRead(String userId, String sortKey) {
        update(userId, sortKey, "SET isRead = :read, readAt = if_not_exists(readAt, :now)", Map.of(
                ":read", AttributeValue.fromBool(true),
                ":now", AttributeValue.fromS(Instant.now().toString())
        ));
    }

    public void softDelete(String userId, String sortKey) {
        update(userId, sortKey, "SET deletedAt = if_not_exists(deletedAt, :now)",
                Map.of(":now", AttributeValue.fromS(Instant.now().toString())));
    }

    public void restore(String userId, String sortKey) {
        update(userId, sortKey, "REMOVE deletedAt", Map.of());
    }

    private QueryRequest.Builder queryForUser() {
        return QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk");
    }

    private void update(String userId, String sortKey, String expression, Map<String, AttributeValue> values) {
        var request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("PK", partition(userId), "SK", AttributeValue.fromS(sortKey)))
                .conditionExpression("attribute_exists(SK)")
                .updateExpression(expression);
        if (!values.isEmpty()) request.expressionAttributeValues(values);
        try {
            dynamoDb.updateItem(request.build());
        } catch (ConditionalCheckFailedException e) {
            throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    private static AttributeValue partition(String userId) {
        return AttributeValue.fromS("USER#" + userId);
    }

    private static NotificationRecord toResponse(Map<String, AttributeValue> item) {
        return NotificationRecord.builder()
                .id(text(item, "notificationId"))
                .userId(text(item, "userId"))
                .type(text(item, "type"))
                .title(text(item, "title"))
                .message(text(item, "content"))
                .relatedEntityId(text(item, "referenceId"))
                .relatedEntityType(text(item, "referenceType"))
                .read(item.containsKey("isRead") && Boolean.TRUE.equals(item.get("isRead").bool()))
                .deleted(item.containsKey("deletedAt"))
                .createdAt(dateTime(item, "createdAt"))
                .readAt(dateTime(item, "readAt"))
                .build();
    }

    private static String text(Map<String, AttributeValue> item, String attribute) {
        AttributeValue value = item.get(attribute);
        return value == null ? null : value.s();
    }

    private static LocalDateTime dateTime(Map<String, AttributeValue> item, String attribute) {
        String value = text(item, attribute);
        return value == null ? null : LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
    }

    public record QueryResult(List<NotificationRecord> items, Map<String, AttributeValue> lastEvaluatedKey) {}
    public record StoredNotification(String sortKey, NotificationRecord response) {}
}
