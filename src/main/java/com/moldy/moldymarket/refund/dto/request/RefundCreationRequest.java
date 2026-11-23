package com.moldy.moldymarket.refund.dto.request;

import com.moldy.moldymarket.refund.enums.RefundReason;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundCreationRequest(
        UUID orderId,
        UUID buyerId,
        UUID disputeId,
        RefundReason reason,
        BigDecimal amount
) {
}
