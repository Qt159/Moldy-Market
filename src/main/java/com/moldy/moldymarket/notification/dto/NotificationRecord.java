package com.moldy.moldymarket.notification.dto.response;

import java.time.Instant;

public record NotificationRecord(
        String id,
        String userId,
        String type,
        String title,
        String content,
        String referenceId,
        String referenceType,
        boolean isRead,
        boolean isDeleted,
        Instant createdAt,
        Instant readAt
) {
}