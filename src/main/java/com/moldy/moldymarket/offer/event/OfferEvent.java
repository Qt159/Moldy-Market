package com.moldy.moldymarket.offer.event;

import com.moldy.moldymarket.offer.entity.Offer;
import com.moldy.moldymarket.offer.enums.OfferStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record OfferEvent(
    OfferStatus status,
    Offer offer,
    UUID recipientId
) {
}
