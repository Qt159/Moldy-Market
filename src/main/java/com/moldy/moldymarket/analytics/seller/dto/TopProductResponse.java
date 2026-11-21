
package com.moldy.moldymarket.analytics.seller.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class TopProductResponse {

    private UUID productId;

    private String productName;

    private Long totalSold;

    private BigDecimal totalRevenue;
}
