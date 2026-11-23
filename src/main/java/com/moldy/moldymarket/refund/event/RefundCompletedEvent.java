package com.moldy.moldymarket.refund.event;

import com.moldy.moldymarket.refund.enums.RefundDestination;
import com.moldy.moldymarket.refund.enums.RefundReason;

import java.util.UUID;

public record RefundCompletedEvent(
        UUID orderId,
        RefundReason reason,
        RefundDestination refundDestination,
        String note
) {
}
