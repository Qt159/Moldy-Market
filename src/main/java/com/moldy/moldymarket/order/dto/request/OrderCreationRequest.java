package com.moldy.moldymarket.order.dto.request;

import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record OrderCreationRequest(
        @NotEmpty @Size(max = 20)
        List<@NotNull UUID> productIds,
        @NotBlank @Size(max = 255)
        String receiverName,
        @NotBlank @Pattern(regexp = "^(\\+84|0)\\d{9,10}$", message = "Invalid Vietnamese phone nunmber")
        String phoneNumber,
        @NotBlank @Size(max = 500)
        String shippingAddress,
        @Size(max = 500)
        String note
) {
}
