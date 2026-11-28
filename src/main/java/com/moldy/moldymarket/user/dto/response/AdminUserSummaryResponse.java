package com.moldy.moldymarket.user.dto.response;

import com.moldy.moldymarket.user.entity.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserSummaryResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserStatus status,
        Integer trustPoints,
        List<String> roles,
        Instant createdAt,
        Instant lockedAt,
        boolean canLock,
        boolean canUnlock
) {
}
