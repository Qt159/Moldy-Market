package com.moldy.moldymarket.review.repository;


import com.moldy.moldymarket.review.entity.StoreReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreReviewRepository extends JpaRepository<StoreReview, UUID> {
    boolean existsByOrderId(UUID orderId);

    Optional<StoreReview> findByOrderId(UUID orderId);

    List<StoreReview> findAllByStoreIdOrderByCreatedAtDesc(UUID storeId);

    List<StoreReview> findAllBySellerIdOrderByCreatedAtDesc(UUID sellerId);
}
