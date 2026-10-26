package com.moldy.moldymarket.pricing.appraiser.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moldy.moldymarket.pricing.entity.PricingMode;
import com.moldy.moldymarket.pricing.entity.PricingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppraiserPricingResponse(
    UUID requestId,
    UUID listingId,
    UUID categoryId,
    UUID sellerId,
    UUID appraiserId,
    String appraiserName,
    PricingMode pricingMode,
    PricingStatus status,
    BigDecimal suggestedPrice,
    String appraiserNote,
    BigDecimal commissionRate,
    Short rejectCount,
    String disclaimer,
    Instant createdAt,
    Instant updatedAt,
    Instant expiredAt
) {}
