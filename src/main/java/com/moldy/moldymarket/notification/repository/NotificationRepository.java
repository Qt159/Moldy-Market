package com.moldy.moldymarket.notification.repository;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

@Repository
public class NotificationRepository {

    private static final String NOTIFICATION_PREFIX = "NOTIFICATION#";
    private static final int LOOKAHEAD_LIMIT = 25;

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public NotificationRepository(
            DynamoDbClient dynamoDbClient,
            @Value("${aws.dynamodb.notifications-table:notifications}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public Map<String, AttributeValue> findById(String userId, String notificationId) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .consistentRead(true)
                .key(key(userId, notificationId))
                .build());
        return response.item().isEmpty() ? null : response.item();
    }

    public PageResult findPage(
            String userId,
            int limit,
            Map<String, AttributeValue> exclusiveStartKey
    ) {
        if (limit < 1) {
            throw new IllegalArgumentException("Page limit must be positive");
        }
        validateStartKey(userId, exclusiveStartKey);

        List<Map<String, AttributeValue>> items = new ArrayList<>(limit);
        Map<String, AttributeValue> startKey = exclusiveStartKey;
        Map<String, AttributeValue> pageEndKey = null;

        while (items.size() < limit) {
            QueryResponse response = query(
                    userId, limit - items.size(), startKey);
            items.addAll(response.items());
            startKey = response.lastEvaluatedKey();

            if (items.size() == limit) {
                // The query limit applies to evaluated rows. Since the request limit
                // equals the number of visible rows still needed, this key is exactly
                // the boundary after the last item returned to the caller.
                pageEndKey = startKey;
                break;
            }
            if (startKey == null || startKey.isEmpty()) {
                break;
            }
        }

        boolean hasMore = pageEndKey != null && !pageEndKey.isEmpty()
                && hasVisibleItemsAfter(userId, pageEndKey);
        return new PageResult(
                List.copyOf(items), hasMore ? Map.copyOf(pageEndKey) : null, hasMore);
    }

    public void updateReadStatus(String userId, String notificationId, boolean read) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":read", bool(read));
        values.put(":now", n(Instant.now().getEpochSecond()));

        String updateExpression;
        if (read) {
            values.put(":readAt", s(Instant.now().toString()));
            updateExpression = "SET isRead = :read, readAt = :readAt";
        } else {
            updateExpression = "SET isRead = :read REMOVE readAt";
        }

        conditionalUpdate(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key(userId, notificationId))
                .updateExpression(updateExpression)
                .conditionExpression("attribute_exists(PK) AND "
                        + "(attribute_not_exists(isDeleted) OR isDeleted = :notDeleted) AND "
                        + "(attribute_not_exists(expiresAt) OR expiresAt > :now)")
                .expressionAttributeValues(merge(values,
                        ":notDeleted", bool(false)))
                .build());
    }

    public void updateDeletedStatus(String userId, String notificationId, boolean deleted) {
        long now = Instant.now().getEpochSecond();
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":now", n(now));

        String condition;
        if (deleted) {
            values.put(":notDeleted", bool(false));
            values.put(":deleted", bool(true));
            condition = "attribute_exists(PK) AND "
                    + "(attribute_not_exists(isDeleted) OR isDeleted = :notDeleted) AND "
                    + "(attribute_not_exists(expiresAt) OR expiresAt > :now)";
        } else {
            values.put(":deleted", bool(true));
            condition = "attribute_exists(PK) AND isDeleted = :deleted AND "
                    + "(attribute_not_exists(expiresAt) OR expiresAt > :now)";
        }

        conditionalUpdate(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key(userId, notificationId))
                .updateExpression("SET isDeleted = :deleted")
                .conditionExpression(condition)
                .expressionAttributeValues(values)
                .build());
    }

    private boolean hasVisibleItemsAfter(
            String userId,
            Map<String, AttributeValue> pageEndKey
    ) {
        Map<String, AttributeValue> probeKey = pageEndKey;
        while (probeKey != null && !probeKey.isEmpty()) {
            QueryResponse response = query(userId, LOOKAHEAD_LIMIT, probeKey);
            if (!response.items().isEmpty()) {
                return true;
            }
            probeKey = response.lastEvaluatedKey();
        }
        return false;
    }

    private QueryResponse query(
            String userId,
            int evaluatedLimit,
            Map<String, AttributeValue> exclusiveStartKey
    ) {
        QueryRequest.Builder request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :prefix)")
                .filterExpression("(attribute_not_exists(isDeleted) OR isDeleted = :notDeleted) "
                        + "AND (attribute_not_exists(expiresAt) OR expiresAt > :now)")
                .expressionAttributeValues(Map.of(
                        ":pk", s(partitionKey(userId)),
                        ":prefix", s(NOTIFICATION_PREFIX),
                        ":notDeleted", bool(false),
                        ":now", n(Instant.now().getEpochSecond())))
                .scanIndexForward(false)
                .limit(evaluatedLimit);

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            request.exclusiveStartKey(exclusiveStartKey);
        }
        return dynamoDbClient.query(request.build());
    }

    private void validateStartKey(
            String userId,
            Map<String, AttributeValue> startKey
    ) {
        if (startKey == null || startKey.isEmpty()) return;
        AttributeValue pk = startKey.get("PK");
        AttributeValue sk = startKey.get("SK");
        if (pk == null || pk.s() == null || !partitionKey(userId).equals(pk.s())
                || sk == null || sk.s() == null || !sk.s().startsWith(NOTIFICATION_PREFIX)) {
            throw new AppException(ErrorCode.PAGINATION_TOKEN_USER_MISMATCH);
        }
    }

    private void conditionalUpdate(UpdateItemRequest request) {
        try {
            dynamoDbClient.updateItem(request);
        } catch (ConditionalCheckFailedException exception) {
            // Same response for missing, expired, deleted, and foreign-partition items
            // prevents callers from probing another user's notification IDs.
            throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    private Map<String, AttributeValue> key(String userId, String notificationId) {
        if (userId == null || userId.isBlank()
                || notificationId == null || notificationId.isBlank()) {
            throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        return Map.of("PK", s(partitionKey(userId)),
                "SK", s(NOTIFICATION_PREFIX + notificationId));
    }

    private String partitionKey(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        return "USER#" + userId;
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

    private AttributeValue n(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }

    private AttributeValue bool(boolean value) {
        return AttributeValue.builder().bool(value).build();
    }

    public record PageResult(
            List<Map<String, AttributeValue>> items,
            Map<String, AttributeValue> nextToken,
            boolean hasMore
    ) {
    }
}
