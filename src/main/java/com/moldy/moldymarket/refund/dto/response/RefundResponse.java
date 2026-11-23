package com.moldy.moldymarket.refund.dto.response;

import com.moldy.moldymarket.refund.enums.RefundStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundResponse(
        UUID refundId,
        BigDecimal amount,
        RefundStatus status
) {
}
