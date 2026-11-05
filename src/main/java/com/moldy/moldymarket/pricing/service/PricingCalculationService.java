package com.moldy.moldymarket.pricing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/* suggestedPrice = referencePrice × deprecationFactor × conditionFactor × warrantyFactor
   Kết quả được làm tròn đến 10,000 VND.
 */
public class PricingCalculationService {
    private static final BigDecimal ROUNDING_UNIT = new BigDecimal("10000");
    private static final int CALCULATION_SCALE = 10;  // Độ chính xác khi tính toán

    public static long calculateSuggestedPrice(BigDecimal referencePrice,
                                               BigDecimal deprecationFactor,
                                               BigDecimal conditionFactor,
                                               BigDecimal warrantyFactor) {
        if (referencePrice == null || referencePrice.signum() <= 0) {
            throw new IllegalArgumentException("referencePrice must be positive");
        }
        if (deprecationFactor == null || deprecationFactor.signum() <= 0) {
            throw new IllegalArgumentException("deprecationFactor must be positive");
        }
        if (conditionFactor == null || conditionFactor.signum() <= 0) {
            throw new IllegalArgumentException("conditionFactor must be positive");
        }
        if (warrantyFactor == null || warrantyFactor.signum() <= 0) {
            throw new IllegalArgumentException("warrantyFactor must be positive");
        }

        BigDecimal suggestedPrice = referencePrice
            .multiply(deprecationFactor, new java.math.MathContext(CALCULATION_SCALE, RoundingMode.HALF_UP))
            .multiply(conditionFactor, new java.math.MathContext(CALCULATION_SCALE, RoundingMode.HALF_UP))
            .multiply(warrantyFactor, new java.math.MathContext(CALCULATION_SCALE, RoundingMode.HALF_UP));


        BigDecimal rounded = suggestedPrice
            .divide(ROUNDING_UNIT, 0, RoundingMode.HALF_UP)
            .multiply(ROUNDING_UNIT);

        return rounded.longValue();
    }


    public static long roundToNearestTenThousand(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("value must be positive");
        }
        
        BigDecimal rounded = value
            .divide(ROUNDING_UNIT, 0, RoundingMode.HALF_UP)
            .multiply(ROUNDING_UNIT);
        
        return rounded.longValue();
    }
}
