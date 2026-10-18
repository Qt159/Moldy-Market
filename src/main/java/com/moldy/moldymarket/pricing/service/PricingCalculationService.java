package com.moldy.moldymarket.pricing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service để tính giá Auto Pricing theo công thức.
 * Formula: suggestedPrice = referencePrice × deprecationFactor × conditionFactor × warrantyFactor
 * Kết quả được làm tròn đến 10,000 VND.
 */
public class PricingCalculationService {
    private static final BigDecimal ROUNDING_UNIT = new BigDecimal("10000");
    private static final int CALCULATION_SCALE = 10;  // Độ chính xác khi tính toán

    /**
     * Tính giá đề xuất (suggestedPrice).
     * 
     * @param referencePrice Giá tham chiếu (đơn vị: VND)
     * @param deprecationFactor Hệ số khấu hao theo thời gian sử dụng
     * @param conditionFactor Hệ số tình trạng sản phẩm
     * @param warrantyFactor Hệ số bảo hành
     * @return Giá đề xuất đã làm tròn đến 10,000 VND
     */
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

        // Tính toán với độ chính xác cao
        BigDecimal suggestedPrice = referencePrice
            .multiply(deprecationFactor, new java.math.MathContext(CALCULATION_SCALE, RoundingMode.HALF_UP))
            .multiply(conditionFactor, new java.math.MathContext(CALCULATION_SCALE, RoundingMode.HALF_UP))
            .multiply(warrantyFactor, new java.math.MathContext(CALCULATION_SCALE, RoundingMode.HALF_UP));

        // Làm tròn đến 10,000 VND
        BigDecimal rounded = suggestedPrice
            .divide(ROUNDING_UNIT, 0, RoundingMode.HALF_UP)
            .multiply(ROUNDING_UNIT);

        return rounded.longValue();
    }

    /**
     * Helper để làm tròn một số theo 10,000 VND.
     */
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
