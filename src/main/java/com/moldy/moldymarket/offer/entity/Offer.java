
package com.moldy.moldymarket.offer.entity;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.offer.enums.OfferSide;
import com.moldy.moldymarket.offer.enums.OfferStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false, updatable = false)
    UUID productId;

    @Column(nullable = false, updatable = false)
    UUID buyerId;

    @Column(nullable = false, updatable = false)
    UUID sellerId;

    @Column(nullable = false, updatable = false)
    UUID storeId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    BigDecimal originalPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    OfferSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    OfferStatus status;

    @Column(nullable = false, updatable = false)
    UUID negotiationId;

    @Column(updatable = false)
    UUID previousOfferId;

    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant updatedAt;

    // Deadline for responding to this offer
    @Column(nullable = false)
    Instant expiresAt;

    // Deadline for the buyer to purchase after acceptance
    Instant acceptedExpiresAt;

    UUID checkoutGroupId;

    String closeReason;

    @Version
    Long version;

    @Builder
    private Offer(
            UUID productId,
            UUID buyerId,
            UUID sellerId,
            UUID storeId,
            BigDecimal originalPrice,
            BigDecimal price,
            OfferSide side,
            UUID negotiationId,
            UUID previousOfferId,
            Instant expiresAt
    ) {
        this.productId = productId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.storeId = storeId;
        this.originalPrice = originalPrice;
        this.price = price;
        this.side = side;
        this.negotiationId = negotiationId;
        this.previousOfferId = previousOfferId;
        this.status = OfferStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void accept(OfferSide actor, Instant now, Instant acceptedDeadline) {
        requirePending(now);
        validateResponder(actor);

        this.status = OfferStatus.ACCEPTED;
        this.acceptedExpiresAt = acceptedDeadline;
    }

    public void reject(OfferSide actor, Instant now) {
        requirePending(now);
        validateResponder(actor);

        this.status = OfferStatus.REJECTED;
    }

    public void cancel(Instant now) {
        requirePending(now);

        this.status = OfferStatus.CANCELLED;
    }

    public void supersede(Instant now) {
        requirePending(now);

        this.status = OfferStatus.SUPERSEDED;
    }
    public void expire(Instant now) {
        if (this.status != OfferStatus.PENDING) {
            return;
        }

        if (now.isBefore(this.expiresAt)) {
            return;
        }

        this.status = OfferStatus.EXPIRED;
    }

    private void requirePending(Instant now) {
        if (this.status != OfferStatus.PENDING) {
            throw new AppException(ErrorCode.OFFER_INVALID_STATE);
        }

        if (!now.isBefore(this.expiresAt)) {
            throw new AppException(ErrorCode.OFFER_EXPIRED);
        }
    }

    private void validateResponder(OfferSide actor) {
        if (actor == this.side) {
            throw new AppException(ErrorCode.OFFER_NOT_YOUR_TURN);
        }
    }
}
