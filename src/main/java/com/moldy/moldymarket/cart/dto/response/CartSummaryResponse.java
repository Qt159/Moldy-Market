package com.moldy.moldymarket.cart.dto.response;

import com.moldy.moldymarket.cart.entity.CartStatus;

import java.math.BigDecimal;
import java.util.UUID;


public record CartSummaryResponse(
        UUID id,
        CartStatus status,
        BigDecimal subtotal,
        Integer totalItems
) {}
