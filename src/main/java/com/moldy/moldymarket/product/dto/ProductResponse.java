package com.moldy.moldymarket.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moldy.moldymarket.common.storage.dto.response.MediaViewResponse;
import com.moldy.moldymarket.product.entity.PricingStrategy;
import com.moldy.moldymarket.product.entity.ProductCondition;
import com.moldy.moldymarket.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID sellerId,
        UUID storeId,
        UUID categoryId,
        String title,
        String description,
        BigDecimal price,
        ProductCondition condition,
        ProductStatus status,
        PricingStrategy pricingStrategy,
        List<String> images,
        Map<String, Object> attributes,
        Instant createdAt,
        String removalReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long version,
        List<UUID> imageFileIds,
        List<MediaViewResponse> media,
        boolean allowOffer
) {
    public ProductResponse(UUID id, UUID sellerId, UUID storeId, UUID categoryId, String title,
            String description, BigDecimal price, ProductCondition condition, ProductStatus status,
            PricingStrategy pricingStrategy, List<String> images, Map<String, Object> attributes,
            Instant createdAt, String removalReason, Long version) {
        this(id, sellerId, storeId, categoryId, title, description, price, condition, status,
                pricingStrategy, images, attributes, createdAt, removalReason, version, List.of(), List.of(), true);
    }

    public ProductResponse(UUID id, UUID sellerId, UUID storeId, UUID categoryId, String title,
            String description, BigDecimal price, ProductCondition condition, ProductStatus status,
            PricingStrategy pricingStrategy, List<String> images, Map<String, Object> attributes,
            Instant createdAt, String removalReason, Long version, List<UUID> imageFileIds,
            List<MediaViewResponse> media) {
        this(id, sellerId, storeId, categoryId, title, description, price, condition, status,
                pricingStrategy, images, attributes, createdAt, removalReason, version, imageFileIds, media, true);
    }
}
