package com.moldy.moldymarket.payment.dto.response;

import com.moldy.moldymarket.payment.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse (
        UUID id,
        BigDecimal amount,
        String paymentStatus,
        PaymentMethod method,
        String qrBankUrl, UUID checkoutGroupId){
}
