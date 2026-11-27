package com.moldy.moldymarket.offer.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OfferCounterRequest(
        @NotNull @Positive BigDecimal price,
        @Size(max = 300) String note
) {
    public OfferCounterRequest(BigDecimal price) {
        this(price, null);
    }
}
