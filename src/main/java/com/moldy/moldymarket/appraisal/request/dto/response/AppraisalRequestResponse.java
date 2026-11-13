package com.moldy.moldymarket.appraisal.request.dto.response;

import com.moldy.moldymarket.appraisal.request.entity.AppraisalRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AppraisalRequestResponse(
    UUID id,
    UUID listingId,
    UUID sellerId,
    String description,
    BigDecimal expectedPrice,
    AppraisalRequestStatus status,
    Instant deadline,
    Instant createdAt,
    Instant updatedAt
) {}
