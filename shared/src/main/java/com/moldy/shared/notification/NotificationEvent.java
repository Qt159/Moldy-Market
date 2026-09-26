package com.moldy.shared.notification;

import java.time.Instant;

/**
 * Event được publish lên SQS bởi backend.
 * Lambda consume event này để: lưu DynamoDB, gửi SES, push SNS.
 */
public record NotificationEvent(
        String notificationId,  // UUID sinh tại backend
        String userId,          // UUID của user nhận notification
        NotificationType type,
        String referenceType,   // "ORDER" | "OFFER" | "DISPUTE" | "APPRAISAL" | "WALLET"
        String referenceId,     // UUID của entity liên quan
        String title,
        String content,
        Instant createdAt
) {}
