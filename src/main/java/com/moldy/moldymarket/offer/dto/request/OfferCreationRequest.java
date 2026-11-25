package com.moldy.moldymarket.offer.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record OfferCreationRequest(
        @NotNull UUID productId,
        @NotNull @Positive BigDecimal price
) {
}
