package com.moldy.moldymarket.cart.dto.response;

import com.moldy.moldymarket.cart.entity.CartStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


public record CartResponse(
        UUID id,
        UUID userId,
        CartStatus status,
        List<CartItemResponse> items,
        BigDecimal subtotal,
        Integer totalItems,
        Instant createdAt,
        Instant lastUpdatedAt,
        Instant abandonedAt
) {}
