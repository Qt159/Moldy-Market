package com.moldy.moldymarket.offer.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record OfferCreationRequest(
        @NotNull UUID productId,
        @NotNull @Positive BigDecimal price,
        @Size(max = 300) String note
) {
    public OfferCreationRequest(UUID productId, BigDecimal price) {
        this(productId, price, null);
    }
}
