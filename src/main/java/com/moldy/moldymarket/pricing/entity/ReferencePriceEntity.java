package com.moldy.moldymarket.pricing.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

<<<<<<< HEAD
/**
 * Reference Price entity - Giá tham chiếu model điện tử.
 * Được cập nhật bởi Admin theo định kỳ 1-3 tháng/lần.
 */
=======
/* Giá tham chiếu model điện tử.
  Được cập nhật bởi Admin theo định kỳ 1-3 tháng/lần.*/
>>>>>>> origin/feature/voucher
@Entity
@Table(
    name = "reference_prices",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_brand_model", columnNames = {"brand", "model_name"})
    },
    indexes = {
        @Index(name = "idx_ref_prices_category", columnList = "category_id"),
        @Index(name = "idx_ref_prices_brand_model", columnList = "brand, model_name"),
        @Index(name = "idx_ref_prices_updated_at", columnList = "updated_at DESC")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReferencePriceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    UUID categoryId;

    @Column(nullable = false, length = 100)
    String brand;

    @Column(nullable = false, length = 200, name = "model_name")
    String modelName;

    @Column(nullable = false)
    BigDecimal referencePrice;

    @Column(nullable = false, length = 10)
    String currency = "VND";

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "updated_by")
    UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public ReferencePriceEntity(UUID categoryId, String brand, String modelName, 
                               BigDecimal referencePrice, UUID updatedBy) {
        this.categoryId = categoryId;
        this.brand = brand;
        this.modelName = modelName;
        this.referencePrice = referencePrice;
        this.updatedBy = updatedBy;
        this.currency = "VND";
    }

    public void updatePrice(BigDecimal newPrice, UUID updatedBy) {
        this.referencePrice = newPrice;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
