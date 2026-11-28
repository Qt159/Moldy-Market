package com.moldy.moldymarket.user.dto.response;

import com.moldy.moldymarket.store.entity.StoreStatus;

import java.util.UUID;

public record OwnedStore(
        UUID id,
        String name,
        StoreStatus status
) {
}
