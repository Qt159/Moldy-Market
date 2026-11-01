package com.moldy.moldymarket.product.dto;

import com.moldy.moldymarket.product.entity.ProductCondition;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductFilterRequest(
        String keyword,
        UUID categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductCondition condition
) {
}
