package com.moldy.moldymarket.pricing.appraiser.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AppraiserPricingCreateRequest(
    UUID listingId,

    @NotNull(message = "Category ID is required")
    UUID categoryId,

    UUID appraiserId,

    @DecimalMin(value = "0.0", message = "Commission rate cannot be negative")
    @DecimalMax(value = "100.0", message = "Commission rate cannot exceed 100%")
    BigDecimal commissionRate
) {}
