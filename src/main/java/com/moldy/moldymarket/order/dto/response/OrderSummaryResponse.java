package com.moldy.moldymarket.order.dto.response;

import com.moldy.moldymarket.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        String orderCode,
        UUID checkoutGroupId,
        UUID buyerId,
        UUID sellerId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant expiredAt,
        List<OrderItemResponse> items
) {
}
