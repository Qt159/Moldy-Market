package com.moldy.moldymarket.payment.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse (
        UUID id,
        BigDecimal amount,
        String paymentStatus,
        String qrBankUrl, UUID checkoutGroupId){
}
