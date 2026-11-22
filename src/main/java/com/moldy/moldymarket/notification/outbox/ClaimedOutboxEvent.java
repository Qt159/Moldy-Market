package com.moldy.moldymarket.notification.outbox;

import java.util.UUID;

public record ClaimedOutboxEvent(
        UUID id,
        String notificationId,
        String payload,
        int retryCount,
        UUID claimToken
) {
}
