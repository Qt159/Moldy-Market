package com.moldy.moldymarket.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID paymentId,
        UUID checkoutGroupId,
        UUID payerId,
        BigDecimal amount,
        Instant paidAt
) {
}
