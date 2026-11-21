package com.moldy.moldymarket.notification.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Repository
public class NotificationRepository {

    private static final String NOTIFICATION_PREFIX = "NOTIFICATION#";
    private static final String GSI_NAME = "GSI1";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public NotificationRepository(
            DynamoDbClient dynamoDbClient,
            @Value("${app.notification.dynamodb.table-name}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public Map<String, AttributeValue> findById(String userId, String notificationId) {
        QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(tableName)
                .indexName(GSI_NAME)
                .keyConditionExpression("GSI1PK = :pk AND GSI1SK = :sk")
                .expressionAttributeValues(Map.of(
                        ":pk", s("USER#" + userId),
                        ":sk", s("ID#" + notificationId)))
                .limit(1)
                .build());

        return response.items().stream()
                .findFirst()
                .orElse(null);
    }

    public PageResult findPage(
            String userId,
            int limit,
            String lastSortKey
    ) {
        Map<String, AttributeValue> exclusiveStartKey = null;

        if (lastSortKey != null && !lastSortKey.isBlank()) {
            if (!lastSortKey.startsWith(NOTIFICATION_PREFIX)) {
                throw new IllegalArgumentException("Invalid notification cursor");
            }

            exclusiveStartKey = Map.of(
                    "PK", s("USER#" + userId),
                    "SK", s(lastSortKey));
        }

        List<Map<String, AttributeValue>> items = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;

        while (items.size() <= limit) {
            QueryRequest.Builder request = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression(
                            "PK = :pk AND begins_with(SK, :prefix)")
                    .filterExpression("isDeleted = :deleted")
                    .expressionAttributeValues(Map.of(
                            ":pk", s("USER#" + userId),
                            ":prefix", s(NOTIFICATION_PREFIX),
                            ":deleted", bool(false)))
                    .scanIndexForward(false)
                    .limit(Math.max(limit + 1 - items.size(), 1));

            if (exclusiveStartKey != null) {
                request.exclusiveStartKey(exclusiveStartKey);
            }

            QueryResponse response = dynamoDbClient.query(request.build());

            items.addAll(response.items());
            lastEvaluatedKey = response.lastEvaluatedKey();

            if (lastEvaluatedKey == null || items.size() > limit) {
                break;
            }

            exclusiveStartKey = lastEvaluatedKey;
        }

        boolean hasMore = items.size() > limit;

        if (hasMore) {
            items = new ArrayList<>(items.subList(0, limit));
        }

        String nextToken = null;

        if (hasMore && !items.isEmpty()) {
            nextToken = items.get(items.size() - 1).get("SK").s();
        }

        return new PageResult(items, nextToken, hasMore);
    }

    public void updateReadStatus(
            String userId,
            String notificationId,
            boolean read
    ) {
        Map<String, AttributeValue> item = findById(userId, notificationId);

        if (item == null) {
            throw new NoSuchElementException("Notification not found");
        }

        Map<String, AttributeValue> key = Map.of(
                "PK", item.get("PK"),
                "SK", item.get("SK"));

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":read", bool(read));

        String updateExpression;

        if (read) {
            values.put(":now", s(Instant.now().toString()));
            updateExpression = "SET isRead = :read, readAt = :now";
        } else {
            updateExpression = "SET isRead = :read REMOVE readAt";
        }

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression(updateExpression)
                .conditionExpression("isDeleted = :notDeleted")
                .expressionAttributeValues(merge(values, ":notDeleted", bool(false)))
                .build());
    }

    public void updateDeletedStatus(
            String userId,
            String notificationId,
            boolean deleted
    ) {
        Map<String, AttributeValue> item = findById(userId, notificationId);

        if (item == null) {
            throw new NoSuchElementException("Notification not found");
        }

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", item.get("PK"),
                        "SK", item.get("SK")
                ))
                .updateExpression("SET isDeleted = :deleted")
                .expressionAttributeValues(Map.of(
                        ":deleted", bool(deleted)
                ))
                .build());
    }

    private Map<String, AttributeValue> merge(
            Map<String, AttributeValue> source,
            String key,
            AttributeValue value
    ) {
        Map<String, AttributeValue> result = new HashMap<>(source);
        result.put(key, value);
        return result;
    }

    private AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private AttributeValue bool(boolean value) {
        return AttributeValue.builder().bool(value).build();
    }

    public record PageResult(
            List<Map<String, AttributeValue>> items,
            String nextToken,
            boolean hasMore
    ) {
    }
}