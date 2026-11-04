package com.moldy.moldymarket.dispute.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@Entity
@Table(name = "dispute_requests")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DisputeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    UUID id;

    @Column(name = "user_request_id", nullable = false, updatable = false)
    UUID userId;

    @Column(name = "order_id", nullable = false, updatable = false)
    UUID orderId;

    @Column(name = "description", nullable = false, updatable = false)
    String description;

    @Column(name = "images_url", nullable = false, updatable = false)
    String imagesUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;


}
