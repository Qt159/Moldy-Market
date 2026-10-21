package com.moldy.moldymarket.pricing.helper;

import java.math.BigDecimal;
import java.math.RoundingMode;

<<<<<<< HEAD
/**
 * Helper to calculate suggested price and apply rounding.
 * Formula: suggestedPrice = referencePrice × deprecationFactor × conditionFactor × warrantyFactor
 * Result is rounded to nearest 10,000 VND.
 */
public class PricingCalculationHelper {
    private static final BigDecimal ROUNDING_UNIT = new BigDecimal("10000");
    private static final int CALCULATION_SCALE = 10;  // Precision during calculation

    private PricingCalculationHelper() {
        // Utility class - no instances
    }

    /**
     * Calculate suggested price using pricing formula.
     * 
     * @param referencePrice Reference price in VND (must be > 0)
     * @param deprecationFactor Deprecation factor (must be > 0)
     * @param conditionFactor Condition factor (must be > 0)
     * @param warrantyFactor Warranty factor (must be > 0)
     * @return Suggested price rounded to nearest 10,000 VND
     * @throws IllegalArgumentException if any parameter is null or <= 0
     */
=======
/*
suggestedPrice = referencePrice * deprecationFactor * conditionFactor * warrantyFactor
Result is rounded to nearest 10,000 VND.
*/
public class PricingCalculationHelper {
    private static final BigDecimal ROUNDING_UNIT = new BigDecimal("10000");
    private static final int CALCULATION_SCALE = 10;  

    private PricingCalculationHelper() {
    }

    
>>>>>>> origin/feature/voucher
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

        // Calculate with high precision
        BigDecimal suggestedPrice = referencePrice
            .multiply(deprecationFactor, new java.math.MathContext(CALCULATION_SCALE, RoundingMode.HALF_UP))
            .multiply(conditionFactor, new java.math.MathContext(CALCULATION_SCALE, RoundingMode.HALF_UP))
            .multiply(warrantyFactor, new java.math.MathContext(CALCULATION_SCALE, RoundingMode.HALF_UP));

<<<<<<< HEAD
        // Round to nearest 10,000 VND
        return roundToNearestTenThousand(suggestedPrice);
    }

    /**
     * Round a value to nearest 10,000 VND.
     * 
     * @param value Value to round (must be > 0)
     * @return Rounded value
     * @throws IllegalArgumentException if value is null or <= 0
     */
=======
        return roundToNearestTenThousand(suggestedPrice);
    }

>>>>>>> origin/feature/voucher
    public static long roundToNearestTenThousand(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("value must be positive");
        }
<<<<<<< HEAD
        
=======
>>>>>>> origin/feature/voucher
        BigDecimal rounded = value
            .divide(ROUNDING_UNIT, 0, RoundingMode.HALF_UP)
            .multiply(ROUNDING_UNIT);
        
        return rounded.longValue();
    }
}
