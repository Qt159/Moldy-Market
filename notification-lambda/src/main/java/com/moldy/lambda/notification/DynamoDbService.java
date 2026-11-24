package com.moldy.lambda.notification;

import com.moldy.shared.notification.NotificationEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class DynamoDbService {

    private static final long TTL_DAYS = 90;
    private static final long DELIVERY_LEASE_SECONDS = 120;

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbService(DynamoDbClient client) {
        this(client, requireEnvironmentVariable("DYNAMODB_TABLE_NAME"));
    }

    public DynamoDbService(DynamoDbClient client, String tableName) {
        this.client = client;
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("DynamoDB table name is required");
        }
        this.tableName = tableName;
    }

    public void saveNotification(NotificationEvent event) {
        String pk = userPk(event.userId());
        String sk = "NOTIFICATION#" + event.createdAt() + "#" + event.notificationId();

        long expiresAt = Instant.now()
                .plus(TTL_DAYS, ChronoUnit.DAYS)
                .getEpochSecond();

        Map<String, AttributeValue> item = new HashMap<>();

        item.put("PK", AttributeValue.fromS(pk));
        item.put("SK", AttributeValue.fromS(sk));
        item.put("notificationId", AttributeValue.fromS(event.notificationId()));
        item.put("userId", AttributeValue.fromS(event.userId()));
        item.put("type", AttributeValue.fromS(event.type().name()));
        item.put("title", AttributeValue.fromS(event.title()));
        item.put("content", AttributeValue.fromS(event.content()));
        item.put("isRead", AttributeValue.fromBool(false));
        item.put("isDeleted", AttributeValue.fromBool(false));
        item.put("createdAt", AttributeValue.fromS(event.createdAt().toString()));
        item.put("expiresAt", AttributeValue.fromN(String.valueOf(expiresAt)));

        if (event.referenceType() != null) {
            item.put("referenceType", AttributeValue.fromS(event.referenceType()));
        }

        if (event.referenceId() != null) {
            item.put("referenceId", AttributeValue.fromS(event.referenceId()));
        }

        try {
            client.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression(
                            "attribute_not_exists(PK) AND attribute_not_exists(SK)")
                    .build());

        } catch (ConditionalCheckFailedException ignored) {
            // Notification đã tồn tại: idempotent success.

        } catch (DynamoDbException e) {
            throw new RetryableException(
                    "Cannot save notification: "
                            + event.notificationId(), e);
        }
    }

    /** Claim one channel delivery so an SQS retry does not resend completed work. */
    public boolean claimDelivery(NotificationEvent event, String channel) {
        String pk = userPk(event.userId());
        String sk = channel + "#" + event.notificationId();

        long now = Instant.now().getEpochSecond();
        long leaseUntil = now + DELIVERY_LEASE_SECONDS;
        long expiresAt = Instant.now().plus(TTL_DAYS, ChronoUnit.DAYS).getEpochSecond();

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":sending", AttributeValue.fromS("SENDING"));
        values.put(":sent", AttributeValue.fromS("SENT"));
        values.put(":failed", AttributeValue.fromS("FAILED"));
        values.put(":now", AttributeValue.fromN(String.valueOf(now)));
        values.put(":lease", AttributeValue.fromN(
                String.valueOf(leaseUntil)));
        values.put(":expires", AttributeValue.fromN(String.valueOf(expiresAt)));

        try {
            client.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key(pk, sk))
                    .updateExpression(
                            "SET #status = :sending, leaseUntil = :lease, expiresAt = :expires")
                    .conditionExpression(
                            "attribute_not_exists(#status) " +
                            "OR #status = :failed " +
                            "OR (#status = :sending AND leaseUntil < :now)")
                    .expressionAttributeNames(Map.of(
                            "#status", "deliveryStatus"))
                    .expressionAttributeValues(values)
                    .build());

            return true;

        } catch (ConditionalCheckFailedException e) {
            // Đang được xử lý hoặc đã SENT.
            return false;

        } catch (DynamoDbException e) {
            throw new RetryableException(
                    "Cannot claim " + channel + " delivery: "
                            + event.notificationId(), e);
        }
    }

    public void markDeliverySent(NotificationEvent event, String channel) {
        updateDeliveryStatus(event, channel, "SENT", null);
    }

    public void markDeliveryFailed(NotificationEvent event, String channel, String errorMessage) {
        updateDeliveryStatus(event, channel, "FAILED", errorMessage);
    }

    public void markDeliveryPermanentlyFailed(
            NotificationEvent event, String channel, String errorMessage) {
        updateDeliveryStatus(event, channel, "PERMANENT_FAILED", errorMessage);
    }

    public boolean claimEmailDelivery(NotificationEvent event) {
        return claimDelivery(event, "EMAIL");
    }

    public void markEmailSent(NotificationEvent event) {
        markDeliverySent(event, "EMAIL");
    }

    public void markEmailFailed(NotificationEvent event, String errorMessage) {
        markDeliveryFailed(event, "EMAIL", errorMessage);
    }

    private void updateDeliveryStatus(
            NotificationEvent event, String channel, String status, String errorMessage) {
        String pk = userPk(event.userId());
        String sk = channel + "#" + event.notificationId();

        Map<String, String> names = Map.of("#status", "deliveryStatus");

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":status", AttributeValue.fromS(status));

        String updateExpression = "SET #status = :status";

        if (errorMessage != null && !errorMessage.isBlank()) {
            String safeError = errorMessage.length() > 1000
                    ? errorMessage.substring(0, 1000)
                    : errorMessage;
            updateExpression += ", lastError = :error";
            values.put(":error", AttributeValue.fromS(safeError));
        }

        try {
            client.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key(pk, sk))
                    .updateExpression(updateExpression + " REMOVE leaseUntil")
                    .conditionExpression("attribute_exists(PK)")
                    .expressionAttributeNames(names)
                    .expressionAttributeValues(values)
                    .build());

        } catch (DynamoDbException e) {
            throw new RetryableException(
                    "Cannot update " + channel + " delivery status: "
                            + event.notificationId(), e);
        }
    }

    private Map<String, AttributeValue> key(String pk, String sk) {
        return Map.of(
                "PK", AttributeValue.fromS(pk),
                "SK", AttributeValue.fromS(sk));
    }

    private String userPk(String userId) {
        return "USER#" + userId;
    }

    private static String requireEnvironmentVariable(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable " + name + " is required");
        }

        return value;
    }
}
