package com.moldy.moldymarket.appraisal.request.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record CancelAppraisalRequestDto(
    @NotBlank(message = "Cancellation reason is required")
    @Size(max = 1000)
    String reason
) {}
