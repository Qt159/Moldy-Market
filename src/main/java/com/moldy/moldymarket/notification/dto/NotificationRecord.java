package com.moldy.moldymarket.notification.dto;

/**
 * DTO trả về cho client — ánh xạ từ DynamoDB item.
 */
public record NotificationRecord(
        String notificationId,
        String userId,
        String type,
        String referenceType,
        String referenceId,
        String title,
        String content,
        boolean isRead,
        String createdAt,   // ISO-8601 string
        String deletedAt    // null nếu chưa xóa
) {}
