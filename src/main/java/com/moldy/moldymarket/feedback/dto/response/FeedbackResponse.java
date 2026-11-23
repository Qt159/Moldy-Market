package com.moldy.moldymarket.feedback.dto.response;

import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
        UUID id,
        UUID orderId,
        UUID buyerId,
        UUID sellerId,
        Integer rating,
        String comment,
        Instant createdAt,
        Instant updatedAt
) {
}
