package com.moldy.moldymarket.pricing.entity;

import java.math.BigDecimal;

public enum ConditionLevel {
    NEW(new BigDecimal("1.00")),
    SCRATCHED(new BigDecimal("0.90")),
    PARTIAL_DAMAGE(new BigDecimal("0.70")),
    HEAVILY_DAMAGED(new BigDecimal("0.45"));

    private final BigDecimal factor;

    ConditionLevel(BigDecimal factor) {
        this.factor = factor;
    }

    public BigDecimal getFactor() {
        return factor;
    }
}
