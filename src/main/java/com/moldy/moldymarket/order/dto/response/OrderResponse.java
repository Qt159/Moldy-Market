package com.moldy.moldymarket.order.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moldy.moldymarket.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderCode,
        UUID checkoutGroupId,
        UUID buyerId,
        UUID sellerId,
        OrderStatus status,
        UUID storeId,
        BigDecimal totalAmount,
        String receiverName,
        String receiverPhone,
        BigDecimal shippingFee,
        String shippingAddress,
        BigDecimal shopDiscount,
        BigDecimal platformDiscount,
        Instant confirmedAt,
        Instant shippedAt,
        Instant deliveredAt,
        Instant returnDeadline,
        Instant completedAt,
        Instant cancelledAt, Instant createdAt,
        List<OrderItemResponse> items) {
}
