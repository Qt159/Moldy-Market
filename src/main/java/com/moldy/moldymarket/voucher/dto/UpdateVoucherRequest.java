package com.moldy.moldymarket.voucher.dto;

import com.moldy.moldymarket.voucher.model.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateVoucherRequest(
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal discountValue,
        @NotNull @Positive Integer remainingQuantity,
        @NotNull Instant expiredAt,
        @PositiveOrZero BigDecimal minimumOrderValue,
        @Positive BigDecimal maximumDiscount) {
}
