package com.moldy.shared.notification;

import java.time.Instant;

/* Event được publish lên SQS bởi backend
   Lambda dùng event này để: lưu DynamoDB, gửi SES, push SNS.
*/
public record NotificationEvent(
        String notificationId,  // sinh tại backend
        String userId,          // user nhận notification
        NotificationType type,
        String referenceType,   // chuyển tới "ORDER" | "OFFER" | "DISPUTE" | "APPRAISAL" | "WALLET"
        String referenceId,     // id của entity liên quan
        String title,
        String content,
        Instant createdAt
) {}
