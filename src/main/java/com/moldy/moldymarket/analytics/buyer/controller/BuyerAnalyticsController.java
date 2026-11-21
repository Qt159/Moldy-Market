
package com.moldy.moldymarket.analytics.buyer.controller;

import com.moldy.moldymarket.analytics.buyer.dto.ProductPriceHistoryResponse;
import com.moldy.moldymarket.analytics.buyer.service.BuyerAnalyticsService;
import com.moldy.moldymarket.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics/buyer")
@RequiredArgsConstructor
public class BuyerAnalyticsController {

    private final BuyerAnalyticsService analyticsService;

    @GetMapping("/products/{productId}/price-history")
    public ApiResponse<ProductPriceHistoryResponse> getProductPriceHistory(
            @PathVariable UUID productId) {
        return ApiResponse.success(
                analyticsService.getProductPriceHistory(productId));
    }
}
