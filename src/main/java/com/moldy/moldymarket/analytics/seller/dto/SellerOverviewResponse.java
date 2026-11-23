
package com.moldy.moldymarket.analytics.seller.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SellerOverviewResponse {

    private BigDecimal totalRevenue;
    private Long totalOrders;
    private Long activeProducts;
    private Long soldProducts;
}
