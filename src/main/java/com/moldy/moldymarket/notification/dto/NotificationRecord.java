package com.moldy.moldymarket.notification.dto;

// load từ DynamoDB item.
public record NotificationRecord(
        String notificationId,
        String userId,
        String type,
        String referenceType,
        String referenceId,
        String title,
        String content,
        boolean isRead,
        String createdAt,   
        String deletedAt    
) {}
