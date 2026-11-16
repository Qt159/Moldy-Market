package com.moldy.moldymarket.wallet.dto.response;

import com.moldy.moldymarket.wallet.enums.EscrowStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record EscrowHoldResponse (
        Long id,
        Long orderId,
        BigDecimal amount,
        BigDecimal feeAmount,
        EscrowStatus status,
        Instant createdAt,
        Instant resolvedAt){
}
