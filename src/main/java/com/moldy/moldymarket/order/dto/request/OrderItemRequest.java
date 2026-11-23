package com.moldy.moldymarket.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderItemRequest(
        @NotNull
        UUID productId,
        @Min(1)
        @Max(1)
        Integer quantity) {
    public OrderItemRequest {
        if (quantity == null) quantity = 1;
    }
}
