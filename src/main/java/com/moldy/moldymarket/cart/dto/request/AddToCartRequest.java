package com.moldy.moldymarket.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;


public record AddToCartRequest(
        @NotNull(message = "Listing ID is required")
        UUID listingId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        String note
) {}
