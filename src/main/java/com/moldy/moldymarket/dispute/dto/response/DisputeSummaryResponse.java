package com.moldy.moldymarket.dispute.dto.response;

import com.moldy.moldymarket.dispute.entity.DisputeRequest;
import com.moldy.moldymarket.dispute.enums.DisputeReason;
import com.moldy.moldymarket.dispute.enums.DisputeStatus;

import java.time.Instant;
import java.util.UUID;

public record DisputeSummaryResponse(
        UUID id,
        UUID orderId,
        DisputeReason reason,
        DisputeStatus status,
        Instant createdAt
) { }
