package com.moldy.moldymarket.pricing.reference.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record ReferencePriceRequest(
    @NotNull(message = "categoryId is required")
    UUID categoryId,

    @NotBlank(message = "brand is required")
    @Size(min = 1, max = 100, message = "brand must be 1-100 characters")
    String brand,

    @NotBlank(message = "modelName is required")
    @Size(min = 1, max = 200, message = "modelName must be 1-200 characters")
    String modelName,

    @NotNull(message = "referencePrice is required")
    @Positive(message = "referencePrice must be positive")
    BigDecimal referencePrice
) {}
