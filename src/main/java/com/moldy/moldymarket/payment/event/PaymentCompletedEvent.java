package com.moldy.moldymarket.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID paymentId,
        UUID orderId,
        UUID payerId,
        BigDecimal amount,
        Instant paidAt
) {
}
