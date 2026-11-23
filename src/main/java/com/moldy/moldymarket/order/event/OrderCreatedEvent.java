package com.moldy.moldymarket.order.event;

import java.util.UUID;

public record OrderCreatedEvent(UUID orderId, UUID buyerId, UUID sellerId, String orderCode) {
}
