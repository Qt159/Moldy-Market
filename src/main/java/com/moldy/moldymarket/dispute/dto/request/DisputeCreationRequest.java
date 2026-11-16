package com.moldy.moldymarket.dispute.dto.request;

import com.moldy.moldymarket.dispute.enums.DisputeReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record DisputeCreationRequest(
        @NotNull UUID orderId,
        @NotNull DisputeReason reason,
        @NotBlank @Size(max = 2000) String description,
        @Size(max = 5) List<@NotBlank @Size(max = 1000) String> imageUrls) {
}
