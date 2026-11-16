package com.moldy.moldymarket.pricing.auto.helper;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class PricingCalculationHelper {

    private static final BigDecimal ROUNDING_UNIT = new BigDecimal("10000");
    private static final int CALCULATION_SCALE = 10;

    private PricingCalculationHelper() {
        // Utility class - no instances
    }

    /*
     suggestedPrice = referencePrice * depreciationFactor * conditionFactor * warrantyFactor
     Result is rounded to the nearest 10,000 VND.
    */
    public static long calculateSuggestedPrice(
            BigDecimal referencePrice, BigDecimal depreciationFactor,
            BigDecimal conditionFactor, BigDecimal warrantyFactor) {
        if (referencePrice == null || referencePrice.signum() <= 0) {
            throw new IllegalArgumentException("referencePrice must be positive");
        }

        if (depreciationFactor == null || depreciationFactor.signum() <= 0) {
            throw new IllegalArgumentException("depreciationFactor must be positive");
        }

        if (conditionFactor == null || conditionFactor.signum() <= 0) {
            throw new IllegalArgumentException("conditionFactor must be positive");
        }

        if (warrantyFactor == null || warrantyFactor.signum() <= 0) {
            throw new IllegalArgumentException("warrantyFactor must be positive");
        }

        MathContext mathContext = new MathContext(CALCULATION_SCALE, RoundingMode.HALF_UP);
        BigDecimal suggestedPrice = referencePrice
                .multiply(depreciationFactor, mathContext)
                .multiply(conditionFactor, mathContext)
                .multiply(warrantyFactor, mathContext);

        return roundToNearestTenThousand(suggestedPrice);
    }

    public static long roundToNearestTenThousand(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("value must be positive");
        }

        BigDecimal rounded = value
                .divide(ROUNDING_UNIT, 0, RoundingMode.HALF_UP)
                .multiply(ROUNDING_UNIT);

        return rounded.longValueExact();
    }
}