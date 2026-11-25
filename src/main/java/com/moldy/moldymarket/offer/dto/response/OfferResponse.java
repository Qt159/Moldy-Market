package com.moldy.moldymarket.offer.dto.response;

import com.moldy.moldymarket.offer.enums.OfferSide;
import com.moldy.moldymarket.offer.enums.OfferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferResponse(
        UUID id,
        UUID negotiationId,
        UUID previousOfferId,

        UUID productId,
        UUID buyerId,
        UUID sellerId,
        UUID storeId,

        BigDecimal originalPrice,
        BigDecimal price,

        OfferSide side,
        OfferStatus status,
        Instant expiresAt,
        Instant acceptedExpiresAt,

        Instant createdAt,
        Instant updatedAt
) {
}
