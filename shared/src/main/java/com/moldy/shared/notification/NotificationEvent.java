
package com.moldy.shared.notification;

import java.time.Instant;
import java.util.Objects;

public record NotificationEvent(
        String notificationId,
        String userId,
        NotificationType type,
        String referenceType,
        String referenceId,
        String title,
        String content,
        Instant createdAt
) {
    public NotificationEvent {
        requireText(notificationId, "notificationId");
        requireText(userId, "userId");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        requireText(title, "title");
        requireText(content, "content");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
