package com.moldy.moldymarket.cart.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


public record CartItemResponse(
        UUID id,
        UUID listingId,
        UUID sellerId,
        Integer quantity,
        BigDecimal priceAtAddTime,
        BigDecimal totalPrice,
        String note,
        Instant createdAt,
        Instant updatedAt
) {}
