package com.moldy.moldymarket.user.dto.response;

import com.moldy.moldymarket.user.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String email,
        String fullName,
        UserStatus status,
        Integer trustPoints,
        Instant createdAt
) {
}
