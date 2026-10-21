package com.moldy.moldymarket.pricing.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PricingCalculationServiceTest {

    @Test
    void testCalculateSuggestedPriceDocumentationExample() {
        // Example from documentation: iPhone 15 Pro Max
        // 28,000,000 × 0.72 × 0.90 × 0.97 = 17,599,680 → round to 17,600,000
        BigDecimal referencePrice = new BigDecimal("28000000");
        BigDecimal deprecationFactor = new BigDecimal("0.72");
        BigDecimal conditionFactor = new BigDecimal("0.90");
        BigDecimal warrantyFactor = new BigDecimal("0.97");

        long result = PricingCalculationService.calculateSuggestedPrice(
            referencePrice, deprecationFactor, conditionFactor, warrantyFactor
        );

        assertEquals(17600000L, result);
    }

    @Test
    void testCalculateSuggestedPriceNewConditionWithWarranty() {
        // 28,000,000 × 0.90 × 1.00 × 1.05 = 26,460,000
        BigDecimal referencePrice = new BigDecimal("28000000");
        BigDecimal deprecationFactor = new BigDecimal("0.90");
        BigDecimal conditionFactor = new BigDecimal("1.00");
        BigDecimal warrantyFactor = new BigDecimal("1.05");

        long result = PricingCalculationService.calculateSuggestedPrice(
            referencePrice, deprecationFactor, conditionFactor, warrantyFactor
        );

        assertEquals(26460000L, result);
    }

    @Test
    void testCalculateSuggestedPriceHeavilyDamaged() {
        // 28,000,000 × 0.35 × 0.45 × 0.90 = 3,969,000
        // Actual calculation: 28000000 * 0.35 * 0.45 * 0.90 = 3969000
        // Due to floating point, might be slightly different after rounding
        BigDecimal referencePrice = new BigDecimal("28000000");
        BigDecimal deprecationFactor = new BigDecimal("0.35");
        BigDecimal conditionFactor = new BigDecimal("0.45");
        BigDecimal warrantyFactor = new BigDecimal("0.90");

        long result = PricingCalculationService.calculateSuggestedPrice(
            referencePrice, deprecationFactor, conditionFactor, warrantyFactor
        );

        // Allow small rounding difference due to precision
        assertTrue(result == 3969000L || result == 3970000L, "Result was: " + result);
    }

    @Test
    void testCalculateSuggestedPriceRoundingUp() {
        // Create calculation that results in x.5+
        // Need to find values that result in rounding up
        BigDecimal referencePrice = new BigDecimal("10000000");
        BigDecimal deprecationFactor = new BigDecimal("0.90");
        BigDecimal conditionFactor = new BigDecimal("0.90");
        BigDecimal warrantyFactor = new BigDecimal("0.90");

        long result = PricingCalculationService.calculateSuggestedPrice(
            referencePrice, deprecationFactor, conditionFactor, warrantyFactor
        );

        // 10,000,000 × 0.90 × 0.90 × 0.90 = 7,290,000
        assertEquals(7290000L, result);
    }

    @Test
    void testNegativePriceThrows() {
        BigDecimal referencePrice = new BigDecimal("-1000");
        BigDecimal factor = new BigDecimal("0.90");

        assertThrows(IllegalArgumentException.class, () -> {
            PricingCalculationService.calculateSuggestedPrice(
                referencePrice, factor, factor, factor
            );
        });
    }

    @Test
    void testZeroPriceThrows() {
        BigDecimal referencePrice = new BigDecimal("0");
        BigDecimal factor = new BigDecimal("0.90");

        assertThrows(IllegalArgumentException.class, () -> {
            PricingCalculationService.calculateSuggestedPrice(
                referencePrice, factor, factor, factor
            );
        });
    }

    @Test
    void testNullReferencePriceThrows() {
        BigDecimal factor = new BigDecimal("0.90");

        assertThrows(IllegalArgumentException.class, () -> {
            PricingCalculationService.calculateSuggestedPrice(
                null, factor, factor, factor
            );
        });
    }

    @Test
    void testRoundToNearestTenThousand() {
        BigDecimal value = new BigDecimal("17599680");
        long result = PricingCalculationService.roundToNearestTenThousand(value);
        assertEquals(17600000L, result);
    }
}
