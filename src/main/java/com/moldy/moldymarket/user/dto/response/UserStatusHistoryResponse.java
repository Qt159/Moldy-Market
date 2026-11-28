package com.moldy.moldymarket.user.dto.response;

import com.moldy.moldymarket.user.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserStatusHistoryResponse(
        UUID id,
        UserStatus fromStatus,
        UserStatus toStatus,
        String reason,
        ActorRef actor,
        Instant createdAt
) {
}
