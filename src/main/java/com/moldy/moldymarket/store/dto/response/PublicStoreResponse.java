package com.moldy.moldymarket.store.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PublicStoreResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt
) {
}
