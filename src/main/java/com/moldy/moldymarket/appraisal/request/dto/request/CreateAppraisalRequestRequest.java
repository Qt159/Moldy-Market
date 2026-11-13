package com.moldy.moldymarket.appraisal.request.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateAppraisalRequestDto(

    @NotNull(message = "Listing ID is required")
    UUID listingId,

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 5000, message = "Description must be between 10 and 5000 characters")
    String description,

    @NotNull(message = "Expected price is required")
    @DecimalMin(value = "0.01", message = "Expected price must be greater than zero")
    BigDecimal expectedPrice,

    @NotNull(message = "Deadline is required")
    @Future(message = "Deadline must be in the future")
    Instant deadline
) {}
