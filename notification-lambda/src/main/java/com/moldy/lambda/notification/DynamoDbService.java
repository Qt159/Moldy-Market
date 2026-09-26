package com.moldy.lambda.notification;

import com.moldy.shared.notification.NotificationEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class DynamoDbService {

    private static final String TABLE_NAME = System.getenv("DYNAMODB_TABLE_NAME");
    // TTL: giữ notification 90 ngày
    private static final long TTL_DAYS = 90;

    private final DynamoDbClient client;

    public DynamoDbService(DynamoDbClient client) {
        this.client = client;
    }

    public void save(NotificationEvent event) {
        String pk = "USER#" + event.userId();
        String sk = "NOTIFICATION#" + event.createdAt().toString() + "#" + event.notificationId();

        long ttlEpoch = Instant.now()
                .plus(TTL_DAYS, ChronoUnit.DAYS)
                .getEpochSecond();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK",              AttributeValue.fromS(pk));
        item.put("SK",              AttributeValue.fromS(sk));
        item.put("notificationId",  AttributeValue.fromS(event.notificationId()));
        item.put("userId",          AttributeValue.fromS(event.userId()));
        item.put("type",            AttributeValue.fromS(event.type().name()));
        item.put("referenceType",   AttributeValue.fromS(event.referenceType()));
        item.put("referenceId",     AttributeValue.fromS(event.referenceId()));
        item.put("title",           AttributeValue.fromS(event.title()));
        item.put("content",         AttributeValue.fromS(event.content()));
        item.put("isRead",          AttributeValue.fromBool(false));
        item.put("createdAt",       AttributeValue.fromS(event.createdAt().toString()));
        item.put("expiresAt",       AttributeValue.fromN(String.valueOf(ttlEpoch)));

        client.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .conditionExpression("attribute_not_exists(PK)") // không ghi đè nếu đã tồn tại
                .build());
    }
}
