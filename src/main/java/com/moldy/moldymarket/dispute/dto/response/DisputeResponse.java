package com.moldy.moldymarket.dispute.dto.response;

import com.moldy.moldymarket.dispute.entity.DisputeRequest;
import com.moldy.moldymarket.dispute.enums.DisputeReason;
import com.moldy.moldymarket.dispute.enums.DisputeStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.moldy.moldymarket.common.storage.dto.response.MediaViewResponse;

public record DisputeResponse (
        UUID id,
        UUID orderId,
        UUID buyerId,
        UUID sellerId,
        DisputeReason reason,
        String description,
        List<String> imageUrls,
        DisputeStatus status,
        UUID reviewedBy,
        Instant reviewedAt,
        String decisionNote,
        Instant createdAt,
        List<UUID> imageFileIds,
        List<MediaViewResponse> media
){
    public DisputeResponse(UUID id, UUID orderId, UUID buyerId, UUID sellerId, DisputeReason reason,
            String description, List<String> imageUrls, DisputeStatus status, UUID reviewedBy,
            Instant reviewedAt, String decisionNote, Instant createdAt) {
        this(id, orderId, buyerId, sellerId, reason, description, imageUrls, status, reviewedBy,
                reviewedAt, decisionNote, createdAt, List.of(), List.of());
    }
}
