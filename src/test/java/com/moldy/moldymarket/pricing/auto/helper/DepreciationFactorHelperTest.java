package com.moldy.moldymarket.pricing.auto.helper;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DepreciationFactorHelperTest {

    @Test
    void testCalculateFactorAt0Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(0);
        assertEquals(new BigDecimal("0.90"), factor);
    }

    @Test
    void testCalculateFactorAt3Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(3);
        assertEquals(new BigDecimal("0.90"), factor);
    }

    @Test
    void testCalculateFactorAt4Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(4);
        assertEquals(new BigDecimal("0.82"), factor);
    }

    @Test
    void testCalculateFactorAt6Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(6);
        assertEquals(new BigDecimal("0.82"), factor);
    }

    @Test
    void testCalculateFactorAt7Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(7);
        assertEquals(new BigDecimal("0.72"), factor);
    }

    @Test
    void testCalculateFactorAt12Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(12);
        assertEquals(new BigDecimal("0.72"), factor);
    }

    @Test
    void testCalculateFactorAt13Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(13);
        assertEquals(new BigDecimal("0.63"), factor);
    }

    @Test
    void testCalculateFactorAt18Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(18);
        assertEquals(new BigDecimal("0.63"), factor);
    }

    @Test
    void testCalculateFactorAt19Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(19);
        assertEquals(new BigDecimal("0.55"), factor);
    }

    @Test
    void testCalculateFactorAt24Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(24);
        assertEquals(new BigDecimal("0.55"), factor);
    }

    @Test
    void testCalculateFactorAt25Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(25);
        assertEquals(new BigDecimal("0.45"), factor);
    }

    @Test
    void testCalculateFactorAt36Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(36);
        assertEquals(new BigDecimal("0.45"), factor);
    }

    @Test
    void testCalculateFactorAt37Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(37);
        assertEquals(new BigDecimal("0.35"), factor);
    }

    @Test
    void testCalculateFactorAt60Months() {
        BigDecimal factor = DepreciationFactorHelper.calculateFactor(60);
        assertEquals(new BigDecimal("0.35"), factor);
    }

    @Test
    void testCalculateFactorNegativeUsageMonthsThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            DepreciationFactorHelper.calculateFactor(-1);
        });
    }
}
