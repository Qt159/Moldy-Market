package com.moldy.moldymarket.notification;

/**
 * DTO trả về cho client — ánh xạ từ DynamoDB item.
 * Thêm deletedAt để hỗ trợ soft delete / restore.
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
        String createdAt,   // ISO-8601 string từ DynamoDB SK
        String deletedAt    // null nếu chưa xóa
) {}
