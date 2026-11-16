package com.moldy.moldymarket.appraisal.review.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AppraisalReviewResponse(

    UUID id,

    UUID assignmentId,

    UUID sellerId,

    UUID appraiserId,

    Integer rating,

    Integer accuracyScore,

    Integer professionalismScore,

    Integer communicationScore,

    Integer timelinessScore,

    String comment,

    Boolean recommend,

    Instant createdAt

) {
}