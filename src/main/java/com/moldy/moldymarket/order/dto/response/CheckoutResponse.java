package com.moldy.moldymarket.order.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CheckoutResponse(
        String id,
        BigDecimal totalAmount,
        Instant expiredAt,
        List<OrderResponse> orders
) {
}
