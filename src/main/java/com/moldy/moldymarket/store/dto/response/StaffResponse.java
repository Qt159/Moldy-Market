package com.moldy.moldymarket.store.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record StaffResponse(
        UUID id,
        UUID userId,
        UUID storeId,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        Set<String> permissions,
        Instant createdAt
) {
}
