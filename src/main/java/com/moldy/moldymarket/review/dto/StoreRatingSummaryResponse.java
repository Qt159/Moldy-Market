package com.moldy.moldymarket.review.dto;

import java.util.List;
import java.util.UUID;

public record StoreRatingSummaryResponse(
        UUID storeId,
        double averageRating,
        long reviewCount,
        List<StoreReviewResponse> reviews
) {
}
