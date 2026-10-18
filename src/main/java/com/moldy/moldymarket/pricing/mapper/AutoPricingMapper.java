package com.moldy.moldymarket.pricing.mapper;

import com.moldy.moldymarket.pricing.dto.AutoPricingResponse;
import com.moldy.moldymarket.pricing.entity.PricingMode;
import com.moldy.moldymarket.pricing.entity.PricingStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mapper for Auto Pricing responses.
 */
@Component
public class AutoPricingMapper {

    /**
     * Build AutoPricingResponse for successful calculation (SUGGESTED status).
     */
    public AutoPricingResponse toSuggestedResponse(long suggestedPrice,
                                                   BigDecimal referencePrice,
                                                   BigDecimal deprecationFactor,
                                                   BigDecimal conditionFactor,
                                                   BigDecimal warrantyFactor,
                                                   String referenceModelName,
                                                   Instant referencePriceUpdatedAt,
                                                   String disclaimer) {
        return new AutoPricingResponse(
            PricingMode.AUTO,
            PricingStatus.SUGGESTED,
            suggestedPrice,
            referencePrice.longValue(),
            deprecationFactor,
            conditionFactor,
            warrantyFactor,
            referenceModelName,
            referencePriceUpdatedAt,
            disclaimer,
            null
        );
    }

    /**
     * Build AutoPricingResponse for manual required case (MANUAL_REQUIRED status).
     */
    public AutoPricingResponse toManualRequiredResponse(String reason) {
        return new AutoPricingResponse(
            PricingMode.AUTO,
            PricingStatus.MANUAL_REQUIRED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            reason
        );
    }
}
