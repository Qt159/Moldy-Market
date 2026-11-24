package com.moldy.moldymarket.review.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_reviews", indexes = {
        @Index(name = "idx_store_reviews_store_created", columnList = "store_id, created_at"),
        @Index(name = "idx_store_reviews_seller_created", columnList = "seller_id, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoreReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    UUID orderId;

    @Column(name = "store_id", updatable = false)
    UUID storeId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    UUID sellerId;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    UUID buyerId;

    @Column(name = "buyer_name", nullable = false, length = 150)
    String buyerName;

    @Column(name = "rating", nullable = false)
    int rating;

    @Column(name = "comment", length = 1000)
    String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @PrePersist
    protected void onCreate(){
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
