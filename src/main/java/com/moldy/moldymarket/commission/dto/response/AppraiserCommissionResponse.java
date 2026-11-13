package com.moldy.moldymarket.commission.dto.response;

import com.moldy.moldymarket.appraiser.enums.VerificationLevel;
import com.moldy.moldymarket.commission.enums.CommissionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AppraiserCommissionResponse(
    UUID id,
    UUID assignmentId,
    UUID appraiserId,
    VerificationLevel verificationLevel,
    BigDecimal agreedFee,
    BigDecimal appraiserRate,
    BigDecimal platformRate,
    BigDecimal appraiserEarnings,
    BigDecimal platformFee,
    CommissionStatus status,
    Instant calculatedAt,
    Instant paidAt
) {
}