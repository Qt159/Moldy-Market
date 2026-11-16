package com.moldy.moldymarket.payment.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ConfirmPaymentRequest(
        @NotBlank @Size(max = 64) String bankReference,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount
) {}
