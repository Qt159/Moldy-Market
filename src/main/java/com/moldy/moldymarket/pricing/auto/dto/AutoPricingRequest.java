package com.moldy.moldymarket.pricing.auto.dto;

import com.moldy.moldymarket.pricing.entity.ConditionLevel;
import com.moldy.moldymarket.pricing.entity.WarrantyStatus;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record AutoPricingRequest(
    @NotNull(message = "categoryId is required")
    UUID categoryId,

    @NotNull(message = "modelId is required")
    UUID modelId,

    @NotNull(message = "origin is required")
    @Pattern(regexp = "OFFICIAL_VN", message = "origin must be OFFICIAL_VN")
    String origin,

    @NotNull(message = "warrantyStatus is required")
    WarrantyStatus warrantyStatus,

    @NotNull(message = "conditionLevel is required")
    ConditionLevel conditionLevel,

    @NotNull(message = "usageMonths is required")
    @Min(value = 0, message = "usageMonths must be >= 0")
    Integer usageMonths
) {}
