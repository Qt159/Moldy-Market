package com.moldy.moldymarket.product.dto;

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
        String removalReason
) {
}
