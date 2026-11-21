
package com.moldy.moldymarket.analytics.buyer.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ProductPriceHistoryResponse {

    private UUID productId;

    private String productName;

    private BigDecimal currentPrice;

    private List<PriceHistoryItem> history;

    @Getter
    @Builder
    public static class PriceHistoryItem {

        private BigDecimal oldPrice;

        private BigDecimal newPrice;

        private Instant changedAt;
    }
}
