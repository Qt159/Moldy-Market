package com.moldy.moldymarket.refund.event;

import com.moldy.moldymarket.refund.enums.RefundReason;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequestEvent(
        UUID orderId,
        UUID buyerId,
        UUID disputeId,
        RefundReason reason,
        BigDecimal amount
        ) {
}
