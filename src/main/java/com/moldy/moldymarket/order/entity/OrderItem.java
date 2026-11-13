package com.moldy.moldymarket.order.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_items", indexes = @Index(name = "idx_order_items_product", columnList = "product_id"))
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    Order order;

    @Column(name = "product_id", nullable = false, updatable = false)
    UUID productId;

    @Column(name = "product_name", nullable = false, updatable = false)
    String productName;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 19, scale = 2)
    BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false, updatable = false)
    int quantity;

    String imageUrl;
}
