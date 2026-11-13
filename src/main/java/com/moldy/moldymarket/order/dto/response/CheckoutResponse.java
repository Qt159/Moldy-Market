package com.moldy.moldymarket.order.dto.response;

import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.payment.dto.response.PaymentResponse;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record CheckoutResponse(
        UUID id,
        BigDecimal totalAmount,
        Instant expiredAt,
        List<OrderSummaryResponse> orders,
        CheckoutGroupStatus status,
        PaymentResponse paymentResponse)
{}
