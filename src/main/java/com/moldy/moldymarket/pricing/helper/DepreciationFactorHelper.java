package com.moldy.moldymarket.pricing.helper;

import java.math.BigDecimal;

<<<<<<< HEAD
/**
 * Helper to calculate Depreciation Factor based on usage months.
 * Applies hardcoded boundaries per documentation.
 */
=======
//khấu hao
>>>>>>> origin/feature/voucher
public class DepreciationFactorHelper {
    private static final int[] BOUNDARIES = {0, 3, 6, 12, 18, 24, 36};
    private static final BigDecimal[] FACTORS = {
        new BigDecimal("0.90"),
        new BigDecimal("0.82"),
        new BigDecimal("0.72"),
        new BigDecimal("0.63"),
        new BigDecimal("0.55"),
        new BigDecimal("0.45"),
        new BigDecimal("0.35")
    };

    private DepreciationFactorHelper() {
<<<<<<< HEAD
        // Utility class - no instances
    }

    /**
     * Calculate depreciation factor based on usage months.
     * 
     * Boundaries:
     * - 0–3 months: 0.90
     * - 4–6 months: 0.82
     * - 7–12 months: 0.72
     * - 13–18 months: 0.63
     * - 19–24 months: 0.55
     * - 25–36 months: 0.45
     * - > 36 months: 0.35
     * 
     * @param usageMonths Number of months used (must be >= 0)
     * @return Depreciation factor as BigDecimal
     * @throws IllegalArgumentException if usageMonths < 0
     */
=======
    }

    
     /*
     - 0–3 months: 0.90
     - 4–6 months: 0.82
     - 7–12 months: 0.72
     - 13–18 months: 0.63
     - 19–24 months: 0.55
     - 25–36 months: 0.45
     - > 36 months: 0.35
    */
>>>>>>> origin/feature/voucher
    public static BigDecimal calculateFactor(int usageMonths) {
        if (usageMonths < 0) {
            throw new IllegalArgumentException("usageMonths must be >= 0");
        }

        if (usageMonths >= 37) {
<<<<<<< HEAD
            return FACTORS[6];  // > 36 months
=======
            return FACTORS[6]; 
>>>>>>> origin/feature/voucher
        }

        for (int i = 0; i < BOUNDARIES.length - 1; i++) {
            int lowerBound = BOUNDARIES[i];
            int upperBound = BOUNDARIES[i + 1];

            if (usageMonths >= lowerBound && usageMonths <= upperBound) {
                return FACTORS[i];
            }
        }
<<<<<<< HEAD

=======
>>>>>>> origin/feature/voucher
        // Default: > 36 months
        return FACTORS[6];
    }
}
