package com.moldy.moldymarket.product.entity;

import com.moldy.moldymarket.product.search.SearchTextNormalizer;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "products")
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    UUID id;

    @Column(name = "seller_id", nullable = false)
    UUID sellerId; // luôn là user thực hiện request

    @Column(name = "store_id")
    UUID storeId; // null = sản phẩm cá nhân

    @Column(nullable = false, length = 150)
    UUID categoryId;

    @Column(nullable = false, length = 150)
    String title;

    @Column(nullable = false, length = 150)
    String description;

    @Column(nullable = false, precision = 14, scale = 2)
    BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ProductCondition condition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ProductStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_strategy", nullable = false, length = 20)
    PricingStrategy pricingStrategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    List<String> images = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    Map<String, Object> attributes = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

    @Column(name = "removal_reason", length = 500)
    String removalReason;

    @Column(name = "removed_by")
    UUID removedBy;

    @Column(name = "removed_at")
    Instant removedAt;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @Column(name = "title_search", nullable = false, columnDefinition = "text not null default ''")
    String titleSearch = "";

    @Column(name = "search_text", nullable = false, columnDefinition = "text not null default ''")
    String searchText = "";

    public Product(UUID sellerId, UUID storeId, UUID categoryId, String title, String description,
                   BigDecimal price, ProductCondition condition, PricingStrategy pricingStrategy,
                   List<String> images, Map<String, Object> attributes) {
        this.sellerId = sellerId;
        this.storeId = storeId;
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.condition = condition;
        this.pricingStrategy = pricingStrategy;
        this.status = ProductStatus.ACTIVE;
        if (images != null) this.images = images;
        if (attributes != null) this.attributes = attributes;
        refreshSearchFields();
    }

    public void update(String title, String description, BigDecimal price, ProductCondition condition,
                       List<String> images, Map<String, Object> attributes) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (price != null) this.price = price;
        if (condition != null) this.condition = condition;
        if (images != null) this.images = images;
        if (attributes != null) this.attributes = attributes;
        this.updatedAt = Instant.now();
        refreshSearchFields();
    }

    public void changeStatus(ProductStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public boolean belongsToStore() {
        return storeId != null;
    }

    public void remove(UUID adminId, String reason) {
        this.status = ProductStatus.REMOVED;
        this.removalReason = reason;
        this.removedBy = adminId;
        this.removedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void restore() {
        this.status = ProductStatus.ACTIVE;
        this.removalReason = null;
        this.removedBy = null;
        this.removedAt = null;
        this.updatedAt = Instant.now();
    }

    private void refreshSearchFields() {
        this.titleSearch = SearchTextNormalizer.normalize(titleSearch);
        this.searchText = titleSearch + " " + SearchTextNormalizer.normalize(description);
    }
}
