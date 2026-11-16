package com.moldy.moldymarket.payment.webhook;

import com.moldy.moldymarket.payment.enums.TransferType;

import java.math.BigDecimal;
import java.time.Instant;

public record IncomingTransfer(
        String provider,
        String externalId,
        Instant transactionTime,
        String accountNumber,
        String gateway,
        String subAccount,
        String content,
        String description,
        String paymentCode,
        TransferType type,
        BigDecimal amount,
        String referenceCode,
        String rawPayload
) {
}
