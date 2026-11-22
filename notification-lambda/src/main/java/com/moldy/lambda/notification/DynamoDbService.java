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

    private static final String TABLE_NAME = requireEnvironmentVariable("DYNAMODB_TABLE_NAME");
    private static final long TTL_DAYS = 90;
    private static final long EMAIL_LEASE_SECONDS = 120;

    private final DynamoDbClient client;

    public DynamoDbService(DynamoDbClient client) {
        this.client = client;
    }

    public void saveNotification(NotificationEvent event) {
        String pk = userPk(event.userId());
        String sk = "NOTIFICATION#" + event.notificationId();

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
                    .tableName(TABLE_NAME)
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

    /* quyền gửi email.
      - Chưa có delivery record.
      - Lần gửi trước FAILED.
      - Lần gửi trước SENDING nhưng lease đã hết hạn.
      SENT sẽ không được claim lại.
    */
    public boolean claimEmailDelivery(NotificationEvent event) {
        String pk = userPk(event.userId());
        String sk = "EMAIL#" + event.notificationId();

        long now = Instant.now().getEpochSecond();
        long leaseUntil = now + EMAIL_LEASE_SECONDS;

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":sending", AttributeValue.fromS("SENDING"));
        values.put(":sent", AttributeValue.fromS("SENT"));
        values.put(":failed", AttributeValue.fromS("FAILED"));
        values.put(":now", AttributeValue.fromN(String.valueOf(now)));
        values.put(":lease", AttributeValue.fromN(
                String.valueOf(leaseUntil)));

        try {
            client.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key(pk, sk))
                    .updateExpression(
                            "SET #status = :sending, leaseUntil = :lease")
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
                    "Cannot claim email delivery: "
                            + event.notificationId(), e);
        }
    }

    public void markEmailSent(NotificationEvent event) {
        updateEmailStatus(event, "SENT", null);
    }

    public void markEmailFailed(NotificationEvent event, String errorMessage) {
        updateEmailStatus(event, "FAILED", errorMessage);
    }

    private void updateEmailStatus(NotificationEvent event, String status, String errorMessage) {
        String pk = userPk(event.userId());
        String sk = "EMAIL#" + event.notificationId();

        Map<String, String> names = Map.of("#status", "deliveryStatus");

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":status", AttributeValue.fromS(status));

        String updateExpression = "SET #status = :status REMOVE leaseUntil";

        if (errorMessage != null && !errorMessage.isBlank()) {
            String safeError = errorMessage.length() > 1000
                    ? errorMessage.substring(0, 1000)
                    : errorMessage;
            updateExpression += ", lastError = :error";
            values.put(":error", AttributeValue.fromS(safeError));
        }

        try {
            client.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key(pk, sk))
                    .updateExpression(updateExpression)
                    .conditionExpression("attribute_exists(PK)")
                    .expressionAttributeNames(names)
                    .expressionAttributeValues(values)
                    .build());

        } catch (DynamoDbException e) {
            throw new RetryableException(
                    "Cannot update email delivery status: "
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
