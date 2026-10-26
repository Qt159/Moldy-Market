package com.moldy.moldymarket.pricing.appraiser.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AppraiserPricingSubmitRequest(
    @NotNull(message = "Suggested price is required")
    @Positive(message = "Suggested price must be greater than zero")
    BigDecimal suggestedPrice,

    @NotBlank(message = "Appraisal note is required")
    String note
) {}
