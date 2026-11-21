
package com.moldy.moldymarket.analytics.buyer.service;

import com.moldy.moldymarket.analytics.buyer.dto.ProductPriceHistoryResponse;
import com.moldy.moldymarket.analytics.buyer.entity.ProductPriceHistory;
import com.moldy.moldymarket.analytics.buyer.repository.BuyerAnalyticsRepository;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuyerAnalyticsService {

    private final BuyerAnalyticsRepository analyticsRepository;
    private final ProductRepository productRepository;

    public ProductPriceHistoryResponse getProductPriceHistory(UUID productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        List<ProductPriceHistoryResponse.PriceHistoryItem> history =
                analyticsRepository
                        .findByProductIdOrderByChangedAtAsc(productId)
                        .stream()
                        .map(this::toHistoryItem)
                        .toList();

        return ProductPriceHistoryResponse.builder()
                .productId(product.getId())
                .productName(product.getTitle())
                .currentPrice(product.getPrice())
                .history(history)
                .build();
    }

    private ProductPriceHistoryResponse.PriceHistoryItem toHistoryItem(ProductPriceHistory entity) {
        return ProductPriceHistoryResponse.PriceHistoryItem.builder()
                .oldPrice(entity.getOldPrice())
                .newPrice(entity.getNewPrice())
                .changedAt(entity.getChangedAt())
                .build();
    }
}
