
package com.moldy.moldymarket.analytics.seller.service;

import com.moldy.moldymarket.analytics.seller.dto.RevenueChartResponse;
import com.moldy.moldymarket.analytics.seller.dto.SellerOverviewResponse;
import com.moldy.moldymarket.analytics.seller.dto.TopProductResponse;
import com.moldy.moldymarket.analytics.seller.repository.SellerAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerAnalyticsService {

    private final SellerAnalyticsRepository analyticsRepository;

    private static final int DEFAULT_TOP_LIMIT = 5;
    private static final int MAX_TOP_LIMIT = 20;

    private static final ZoneId ZONE_ID =ZoneId.of("Asia/Ho_Chi_Minh");

  
    public SellerOverviewResponse getOverview(UUID sellerId) {
        return SellerOverviewResponse.builder()
                .totalRevenue(analyticsRepository.getTotalRevenue(sellerId))
                .totalOrders(analyticsRepository.countCompletedOrders(sellerId))
                .activeProducts(analyticsRepository.countActiveProducts(sellerId))
                .soldProducts(analyticsRepository.countSoldProducts(sellerId))
                .build();
    }

  
    public List<RevenueChartResponse> getRevenueChart(UUID sellerId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZONE_ID);
        if (to == null) {
            to = today;}
        if (from == null) {
            from = to.minusDays(29);}

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("From date must not be after to date");
        }

        Instant fromDate = from.atStartOfDay(ZONE_ID).toInstant();

        Instant toDate = to.plusDays(1)
                .atStartOfDay(ZONE_ID)
                .toInstant();

        return analyticsRepository
                .getRevenueChart(sellerId, fromDate, toDate)
                .stream()
                .map(item -> RevenueChartResponse.builder()
                        .date(item.getRevenueDate())
                        .revenue(item.getRevenue())
                        .orderCount(item.getOrderCount())
                        .build())
                .toList();
    }


    public List<TopProductResponse> getTopProducts(UUID sellerId, Integer limit) {

        int resultLimit = limit == null
                ? DEFAULT_TOP_LIMIT
                : limit;

        if (resultLimit < 1 || resultLimit > MAX_TOP_LIMIT) {
            throw new IllegalArgumentException(
                    "Limit must be between 1 and 20");
        }

        return analyticsRepository
                .getTopProducts(sellerId, resultLimit)
                .stream()
                .map(item -> TopProductResponse.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .totalSold(item.getTotalSold())
                        .totalRevenue(item.getTotalRevenue())
                        .build())
                .toList();
    }
}
