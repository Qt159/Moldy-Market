package com.moldy.moldymarket.appraisal.request.dto.request;

import jakarta.validation.constraints.NotBlank;


public record CancelAppraisalRequestDto(
    @NotBlank(message = "Cancellation reason is required")
    @Size(max = 1000)
    String reason
) {}
