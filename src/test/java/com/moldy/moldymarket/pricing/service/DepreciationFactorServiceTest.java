package com.moldy.moldymarket.pricing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DepreciationFactorServiceTest {

    @Test
    void testCalculateFactorAt0Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(0);
        assertEquals(new BigDecimal("0.90"), factor);
    }

    @Test
    void testCalculateFactorAt3Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(3);
        assertEquals(new BigDecimal("0.90"), factor);
    }

    @Test
    void testCalculateFactorAt4Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(4);
        assertEquals(new BigDecimal("0.82"), factor);
    }

    @Test
    void testCalculateFactorAt6Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(6);
        assertEquals(new BigDecimal("0.82"), factor);
    }

    @Test
    void testCalculateFactorAt7Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(7);
        assertEquals(new BigDecimal("0.72"), factor);
    }

    @Test
    void testCalculateFactorAt12Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(12);
        assertEquals(new BigDecimal("0.72"), factor);
    }

    @Test
    void testCalculateFactorAt13Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(13);
        assertEquals(new BigDecimal("0.63"), factor);
    }

    @Test
    void testCalculateFactorAt18Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(18);
        assertEquals(new BigDecimal("0.63"), factor);
    }

    @Test
    void testCalculateFactorAt19Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(19);
        assertEquals(new BigDecimal("0.55"), factor);
    }

    @Test
    void testCalculateFactorAt24Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(24);
        assertEquals(new BigDecimal("0.55"), factor);
    }

    @Test
    void testCalculateFactorAt25Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(25);
        assertEquals(new BigDecimal("0.45"), factor);
    }

    @Test
    void testCalculateFactorAt36Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(36);
        assertEquals(new BigDecimal("0.45"), factor);
    }

    @Test
    void testCalculateFactorAt37Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(37);
        assertEquals(new BigDecimal("0.35"), factor);
    }

    @Test
    void testCalculateFactorAt60Months() {
        BigDecimal factor = DepreciationFactorService.calculateFactor(60);
        assertEquals(new BigDecimal("0.35"), factor);
    }

    @Test
    void testCalculateFactorNegativeUsageMonthsThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            DepreciationFactorService.calculateFactor(-1);
        });
    }
}
