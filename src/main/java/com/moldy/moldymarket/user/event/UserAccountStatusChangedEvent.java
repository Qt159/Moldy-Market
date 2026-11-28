package com.moldy.moldymarket.user.event;

import com.moldy.moldymarket.user.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserAccountStatusChangedEvent(
        UUID historyId,
        UUID userId,
        UserStatus newStatus,
        String reason,
        Instant changedAt
) {
}
