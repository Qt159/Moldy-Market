package com.moldy.moldymarket.product.repository;

import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductCondition;
import com.moldy.moldymarket.product.entity.ProductStatus;
import com.moldy.moldymarket.store.entity.Store;
import com.moldy.moldymarket.store.entity.StoreStatus;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.UUID;

public final class ProductSpecifications {

    private ProductSpecifications() {}

    public static Specification<Product> keyword(String keyword) {
        return (root, q, cb) -> (keyword == null || keyword.isBlank())
                ? null : cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<Product> categoryId(UUID categoryId) {
        return (root, q, cb) -> categoryId == null
                ? null : cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<Product> priceBetween(BigDecimal min, BigDecimal max) {
        return (root, q, cb) -> {
            if (min == null && max == null) return null;
            if (min != null && max != null) return cb.between(root.get("price"), min, max);
            return min != null ? cb.greaterThanOrEqualTo(root.get("price"), min)
                    : cb.lessThanOrEqualTo(root.get("price"), max);
        };
    }

    public static Specification<Product> condition(ProductCondition condition) {
        return (root, q, cb) -> condition == null
                ? null : cb.equal(root.get("condition"), condition);
    }

    public static Specification<Product> statusActive() {
        return (root, q, cb) ->
                cb.equal(root.get("status"), ProductStatus.ACTIVE);
    }

    public static Specification<Product> hasId(UUID id) {
        return (root, q, cb) -> cb.equal(root.get("id"), id);
    }

    /** Loại sản phẩm của shop đang bị khóa, và sản phẩm cá nhân của user không ACTIVE. */
    public static Specification<Product> sellerAndStoreVisible() {
        return (root, query, cb) -> {
            Subquery<UUID> blockedStoreIds = query.subquery(UUID.class);
            Root<Store> store = blockedStoreIds.from(Store.class);
            blockedStoreIds.select(store.get("id"))
                    .where(cb.equal(store.get("status"), StoreStatus.BLOCKED));

            Subquery<UUID> inactiveUserIds = query.subquery(UUID.class);
            Root<User> user = inactiveUserIds.from(User.class);
            inactiveUserIds.select(user.get("id"))
                    .where(cb.notEqual(user.get("status"), UserStatus.ACTIVE));

            // Sản phẩm shop: shop không bị BLOCKED
            Predicate storeOk = cb.or(
                    cb.isNull(root.get("storeId")),
                    cb.not(root.get("storeId").in(blockedStoreIds)));

            // Sản phẩm cá nhân: người bán phải ACTIVE
            Predicate personalSellerOk = cb.or(
                    cb.isNotNull(root.get("storeId")),
                    cb.not(root.get("sellerId").in(inactiveUserIds)));

            return cb.and(storeOk, personalSellerOk);
        };
    }

}
