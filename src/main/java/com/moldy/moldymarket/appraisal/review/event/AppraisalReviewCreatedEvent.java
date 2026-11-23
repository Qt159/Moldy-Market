package com.moldy.moldymarket.appraisal.review.event;

import java.util.UUID;

public record AppraisalReviewCreatedEvent(
        UUID reviewId,
        UUID assignmentId,
        UUID sellerId,
        UUID appraiserId) {
}
