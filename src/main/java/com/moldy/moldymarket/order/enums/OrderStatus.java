package com.moldy.moldymarket.order.enums;

public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    SHIPPING,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    DISPUTED,
    REFUNDED,
    // Append only: existing rows store ordinal values 0..8.
    PENDING_CONFIRMED,
}
