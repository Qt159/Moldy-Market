
package com.moldy.moldymarket.wishlist.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table( name = "wishlist_items",
        uniqueConstraints = {@UniqueConstraint(
                        name = "uk_wishlist_user_product",
                        columnNames = {"user_id", "product_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "product_id", nullable = false)
    UUID productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    private WishlistItem(UUID userId, UUID productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public static WishlistItem create(UUID userId, UUID productId) {
        return new WishlistItem(userId, productId);
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
