package com.moldy.moldymarket.appraisal.request.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdateAppraisalRequestDto(

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 5000)
    String description,

    @NotNull(message = "Expected price is required")
    @DecimalMin(value = "0.01")
    BigDecimal expectedPrice,

    @NotNull(message = "Deadline is required")
    @Future(message = "Deadline must be in the future")
    Instant deadline
) {}
