
package com.moldy.moldymarket.analytics.buyer.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "product_price_history",
    indexes = {@Index(
            name = "idx_price_history_product_changed_at",
            columnList = "product_id, changed_at")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "product_id", nullable = false)
    UUID productId;

    @Column(name = "old_price", nullable = false, precision = 14, scale = 2)
    BigDecimal oldPrice;

    @Column(name = "new_price", nullable = false, precision = 14, scale = 2)
    BigDecimal newPrice;

    @Column(name = "changed_at", nullable = false, updatable = false)
    Instant changedAt;

    public ProductPriceHistory(
            UUID productId,
            BigDecimal oldPrice,
            BigDecimal newPrice
    ) {
        this.productId = productId;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
        this.changedAt = Instant.now();
    }
}
