package com.moldy.moldymarket.order.event;

import com.moldy.moldymarket.order.enums.OrderStatus;

import java.util.UUID;

public record OrderStatusChangedEvent(
        UUID orderId,
        UUID buyerId,
        UUID sellerId,
        UUID actorId,
        OrderStatus oldStatus,
        OrderStatus newStatus) {
}
