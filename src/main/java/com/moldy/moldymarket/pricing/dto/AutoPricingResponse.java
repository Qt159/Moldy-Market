package com.moldy.moldymarket.pricing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moldy.moldymarket.pricing.entity.PricingMode;
import com.moldy.moldymarket.pricing.entity.PricingStatus;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutoPricingResponse(
    PricingMode pricingMode,
    PricingStatus status,
    Long suggestedPrice,
    Long referencePrice,
    BigDecimal deprecationFactor,
    BigDecimal conditionFactor,
    BigDecimal warrantyFactor,
    String referenceModelName,
    Instant referencePriceUpdatedAt,
    String disclaimer,
    String reason  // MANUAL_REQUIRED/MODEL_NOT_FOUND/CATEGORY_NOT_SUPPORTED
) {}
