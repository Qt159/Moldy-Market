package com.moldy.moldymarket.order.dto.response;

import com.moldy.moldymarket.order.enums.OrderStatus;

import java.util.Map;

public record StoreOrderSummaryResponse(
        long total,
        Map<OrderStatus, Long> counts
) {
}
