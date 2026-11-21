package com.moldy.moldymarket.analytics.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TopProductResponse(
        UUID productId,
        String productName,
        Long quantitySold,
        BigDecimal revenue

) {
}