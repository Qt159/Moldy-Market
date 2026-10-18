package com.moldy.moldymarket.pricing.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

/**
 * Appraiser Category entity - Danh sách category người định giá được phép định giá.
 */
@Entity
@Table(
    name = "appraiser_categories",
    indexes = {
        @Index(name = "idx_appraiser_categories_category", columnList = "category_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@IdClass(AppraiserCategoryId.class)
public class AppraiserCategoryEntity {
    @Id
    @Column(name = "appraiser_id")
    UUID appraiserId;

    @Id
    @Column(name = "category_id")
    UUID categoryId;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public AppraiserCategoryEntity(UUID appraiserId, UUID categoryId) {
        this.appraiserId = appraiserId;
        this.categoryId = categoryId;
    }
}
