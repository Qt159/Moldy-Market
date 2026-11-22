package com.moldy.moldymarket.order.enums;

public enum CheckoutGroupStatus {
    PENDING_PAYMENT,
    PAID,
    CANCELLED,
    EXPIRED,
    // Append only: existing rows store ordinal values 0..3.
    PAY_ON_DELIVERY
}
