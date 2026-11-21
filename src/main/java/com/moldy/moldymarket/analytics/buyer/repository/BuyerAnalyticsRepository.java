
package com.moldy.moldymarket.analytics.buyer.repository;

import com.moldy.moldymarket.analytics.buyer.entity.ProductPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BuyerAnalyticsRepository
        extends JpaRepository<ProductPriceHistory, UUID> {

    List<ProductPriceHistory> findByProductIdOrderByChangedAtAsc(
            UUID productId
    );
}
