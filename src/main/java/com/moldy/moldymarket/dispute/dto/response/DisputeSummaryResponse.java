package com.moldy.moldymarket.dispute.dto.response;

import com.moldy.moldymarket.dispute.enums.DisputeReason;
import com.moldy.moldymarket.dispute.enums.DisputeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DisputeSummaryResponse(
        UUID id,
        UUID orderId,
        String orderCode,
        String firstItemTitle,
        String firstItemImageUrl,
        BigDecimal totalAmount,
        DisputeReason reason,
        DisputeStatus status,
        String decisionNote,
        Instant createdAt
) { }
