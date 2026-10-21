package com.moldy.moldymarket.pricing.entity;

import java.math.BigDecimal;

public enum WarrantyStatus {
    UNDER_WARRANTY(new BigDecimal("1.05")),
    EXPIRED_UNDER_1Y(new BigDecimal("0.97")),
    EXPIRED_OVER_1Y(new BigDecimal("0.90"));

    private final BigDecimal factor;

    WarrantyStatus(BigDecimal factor) {
        this.factor = factor;
    }

    public BigDecimal getFactor() {
        return factor;
    }
}
