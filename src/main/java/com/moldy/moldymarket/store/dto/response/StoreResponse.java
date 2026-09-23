package com.moldy.moldymarket.store.dto.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import com.moldy.moldymarket.store.enums.StoreStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreResponse {
    UUID id;
    UUID ownerId;
    String name;
    String description;
    String pickupAddress;
    StoreStatus status;
    Instant createdAt;
}
