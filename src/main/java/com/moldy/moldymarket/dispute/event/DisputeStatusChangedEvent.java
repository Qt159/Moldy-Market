package com.moldy.moldymarket.dispute.event;

import com.moldy.moldymarket.dispute.enums.DisputeStatus;

import java.util.UUID;

public record DisputeStatusChangedEvent(
        UUID disputeId,
        UUID orderId,
        UUID buyerId,
        UUID sellerId,
        DisputeStatus status
) {
}
