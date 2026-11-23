package com.moldy.moldymarket.wallet.event;

import com.moldy.moldymarket.wallet.enums.DisputeOutcome;

import java.util.UUID;

public record EscrowNotificationEvent(
        String orderId,
        UUID buyerId,
        UUID sellerId,
        Action action,
        DisputeOutcome outcome) {

    public enum Action {
        SELLER_PAID,
        BUYER_REFUNDED,
        DISPUTE_OPENED,
        DISPUTE_RESOLVED
    }
}
