package com.moldy.moldymarket.pricing.service;

import java.math.BigDecimal;

// Tính Depreciation Factor dựa trên số tháng sử dụng.

public class DepreciationFactorService {
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

    /* Tính depreciation factor dựa trên usageMonths. 
        Áp dụng rule:
         - 0–3: 0.90
         - 4–6: 0.82
         - 7–12: 0.72
         - 13–18: 0.63
         - 19–24: 0.55
         - 25–36: 0.45
         - > 36: 0.35
     */
    public static BigDecimal calculateFactor(int usageMonths) {
        if (usageMonths < 0) {
            throw new IllegalArgumentException("usageMonths must be >= 0");
        }

        if (usageMonths >= 37) {
            return FACTORS[6];  // > 36 months
        }

        for (int i = 0; i < BOUNDARIES.length - 1; i++) {
            int lowerBound = BOUNDARIES[i];
            int upperBound = BOUNDARIES[i + 1];

            if (usageMonths >= lowerBound && usageMonths <= upperBound) {
                return FACTORS[i];
            }
        }

        // Default: > 36 months
        return FACTORS[6];
    }
}
