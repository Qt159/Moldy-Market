package com.moldy.moldymarket.order.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderItemRequest(
        @NotNull
        UUID productId) {
}
