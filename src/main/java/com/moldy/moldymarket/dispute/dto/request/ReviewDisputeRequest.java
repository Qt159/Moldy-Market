package com.moldy.moldymarket.dispute.dto.request;

import com.moldy.moldymarket.dispute.enums.DisputeDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewDisputeRequest(
        @NotNull DisputeDecision decision,
        @NotBlank @Size(max = 1000) String note
        ) {
}
