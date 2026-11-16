
package com.moldy.moldymarket.appraisal.application.dto.response;

import com.moldy.moldymarket.appraisal.application.enums.AppraiserApplicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AppraiserApplicationResponse(
    UUID id,
    UUID appraisalRequestId,
    UUID appraiserId,
    String introduction,
    String experienceDescription,
    BigDecimal proposedFee,
    Integer estimatedCompletionHours,
    AppraiserApplicationStatus status,
    Instant appliedAt,
    Instant respondedAt,
    Instant createdAt,
    Instant updatedAt
) {}
