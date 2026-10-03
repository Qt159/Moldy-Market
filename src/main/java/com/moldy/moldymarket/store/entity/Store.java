package com.moldy.moldymarket.store.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import com.moldy.moldymarket.store.enums.StoreStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stores")
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {
    @Id
    @GeneratedValue
    UUID id;

    @Column(name = "owner_id", nullable = false)
    UUID ownerId;

    @Column(nullable = false, unique = true)
    String name;

    String description;

    @Column(name = "pickup_address")
    String pickupAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    StoreStatus status = StoreStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    public Store(UUID ownerId, String name, String description, String pickupAddress) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.pickupAddress = pickupAddress;
    }
}
