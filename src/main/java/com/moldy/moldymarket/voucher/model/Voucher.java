package com.moldy.moldymarket.voucher.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.moldy.moldymarket.store.entity.Store;

@Entity
@Table(
    name = "vouchers",
    indexes = {
        @Index(name = "idx_voucher_code", columnList = "code"),
        @Index(name = "idx_voucher_expired_at", columnList = "expired_at"),
        @Index(name = "idx_voucher_store", columnList = "store_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @NotBlank(message = "Voucher code is required")
    @Column(nullable = false, unique = true, length = 50)
    String code;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    VoucherIssuer issuer = VoucherIssuer.PLATFORM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    Store store;

    @NotNull(message = "Discount type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Discount value must be greater than 0")
    @Column(nullable = false)
    BigDecimal discountValue;

    @NotNull(message = "Remaining quantity is required")
    @PositiveOrZero(message = "Remaining quantity must be greater than or equal to 0")
    @Column(nullable = false)
    Integer remainingQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    Integer reservedQuantity = 0;

    @Column(name = "minimum_order_value", precision = 19, scale = 2)
    BigDecimal minimumOrderValue;

    @Column(name = "maximum_discount", precision = 19, scale = 2)
    BigDecimal maximumDiscount;

    @Column(nullable = false)
    boolean active = true;

    @Version
    @Column(nullable = false)
    Integer version;

    @NotNull(message = "Expiry date is required")
    @Column(nullable = false)
    Instant expiredAt;

    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        version = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
