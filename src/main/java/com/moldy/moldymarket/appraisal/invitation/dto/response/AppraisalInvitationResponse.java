
package com.moldy.moldymarket.appraisal.invitation.dto.response;

import com.moldy.moldymarket.appraisal.invitation.enums.AppraisalInvitationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AppraisalInvitationResponse(
    UUID id,
    UUID appraisalRequestId,
    UUID appraiserId,
    BigDecimal proposedFee,
    String message,
    Instant deadline,
    AppraisalInvitationStatus status,
    Instant respondedAt,
    Instant createdAt,
    Instant updatedAt
) {}
