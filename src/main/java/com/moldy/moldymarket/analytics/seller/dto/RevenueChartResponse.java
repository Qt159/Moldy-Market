
package com.moldy.moldymarket.analytics.seller.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class RevenueChartResponse {

    private LocalDate date;

    private BigDecimal revenue;

    private Long orderCount;
}
