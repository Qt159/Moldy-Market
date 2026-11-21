package com.moldy.moldymarket.analytics.seller.controller;

import com.moldy.moldymarket.analytics.seller.dto.RevenueChartResponse;
import com.moldy.moldymarket.analytics.seller.dto.SellerOverviewResponse;
import com.moldy.moldymarket.analytics.seller.dto.TopProductResponse;
import com.moldy.moldymarket.analytics.seller.service.SellerAnalyticsService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics/seller")
@RequiredArgsConstructor
public class SellerAnalyticsController {

    private final SellerAnalyticsService analyticsService;

    @GetMapping("/overview")
    public ApiResponse<SellerOverviewResponse> getOverview(
                @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.success(analyticsService.getOverview(user.getUserId()));
    }

    @GetMapping("/revenue")
    public ApiResponse<List<RevenueChartResponse>> getRevenueChart(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {
        return ApiResponse.success(
                analyticsService.getRevenueChart(user.getUserId(), from, to));
    }

    @GetMapping("/top-products")
    public ApiResponse<List<TopProductResponse>> getTopProducts(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false, defaultValue = "5")
            Integer limit) {
        return ApiResponse.success(
                analyticsService.getTopProducts(user.getUserId(), limit));
    }
}