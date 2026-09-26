package com.moldy.moldymarket.notification.mapper;

import com.moldy.moldymarket.notification.dto.NotificationRecord;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

/**
 * Map DynamoDB item → NotificationRecord DTO.
 */
public class NotificationMapper {

    private NotificationMapper() {}

    public static NotificationRecord toRecord(Map<String, AttributeValue> item) {
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

    private static String getString(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        return (val != null && val.s() != null) ? val.s() : null;
    }

    private static boolean getBool(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        return val != null && Boolean.TRUE.equals(val.bool());
    }
}
