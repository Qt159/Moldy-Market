package com.moldy.moldymarket.notification.repository;

import com.moldy.moldymarket.notification.dto.NotificationRecord;
import com.moldy.moldymarket.notification.mapper.NotificationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/* table: notifications
   Schema:
   PK = USER#{userId}
   SK = NOTIFICATION#{createdAt}#{notificationId}
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

    public QueryResult findByUserId(String userId, int limit, Map<String, AttributeValue> startKey) {
        QueryRequest.Builder builder = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk")
                .filterExpression("attribute_not_exists(deletedAt)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("USER#" + userId)
                ))
                .scanIndexForward(false)
                .limit(limit);

        if (startKey != null && !startKey.isEmpty()) {
            builder.exclusiveStartKey(startKey);
        }

        QueryResponse response = dynamoDbClient.query(builder.build());

        List<NotificationRecord> items = response.items().stream()
                .map(NotificationMapper::toRecord)
                .toList();

        return new QueryResult(items, response.lastEvaluatedKey());
    }

    public Optional<NotificationRecord> findById(String userId, String notificationId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk")
                .filterExpression("notificationId = :nid")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("USER#" + userId),
                        ":nid", AttributeValue.fromS(notificationId)
                ))
                .build();

        return dynamoDbClient.query(request)
                .items()
                .stream()
                .findFirst()
                .map(NotificationMapper::toRecord);
    }

    public void markAsRead(String userId, String sk) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key(userId, sk))
                .updateExpression("SET isRead = :val")
                .expressionAttributeValues(Map.of(
                        ":val", AttributeValue.fromBool(true)
                ))
                .build());
    }

    public void softDelete(String userId, String sk) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key(userId, sk))
                .updateExpression("SET deletedAt = :ts")
                .expressionAttributeValues(Map.of(
                        ":ts", AttributeValue.fromS(Instant.now().toString())
                ))
                .build());
    }


    public void restore(String userId, String sk) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key(userId, sk))
                .updateExpression("REMOVE deletedAt")
                .build());
    }

    private Map<String, AttributeValue> key(String userId, String sk) {
        return Map.of(
                "PK", AttributeValue.fromS("USER#" + userId),
                "SK", AttributeValue.fromS(sk)
        );
    }

    public record QueryResult(
            List<NotificationRecord> items,
            Map<String, AttributeValue> lastEvaluatedKey
    ) {}
}
