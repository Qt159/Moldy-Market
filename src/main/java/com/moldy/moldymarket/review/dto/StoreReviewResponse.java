package com.moldy.moldymarket.review.dto;

import com.moldy.moldymarket.review.entity.StoreReview;

import java.time.Instant;
import java.util.UUID;

public record StoreReviewResponse(
        UUID id,
        UUID orderId,
        UUID storeId,
        UUID sellerId,
        UUID buyerId,
        String buyerName,
        int rating,
        String comment,
        Instant createdAt
) {
    public static StoreReviewResponse from(StoreReview review) {
        return new StoreReviewResponse(
                review.getId(),
                review.getOrderId(),
                review.getStoreId(),
                review.getSellerId(),
                review.getBuyerId(),
                review.getBuyerName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
