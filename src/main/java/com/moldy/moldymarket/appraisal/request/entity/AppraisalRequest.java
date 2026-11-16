package com.moldy.moldymarket.appraisal.request.entity;

// AppraisalRequestStatus is defined in this same file below
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "appraisal_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppraisalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "expected_price", precision = 19, scale = 2)
    private BigDecimal expectedPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppraisalRequestStatus status = AppraisalRequestStatus.OPEN;

    @Column(name = "deadline")
    private Instant deadline;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AppraisalRequest(UUID listingId, UUID sellerId, String description, BigDecimal expectedPrice, Instant deadline) {
        this.listingId = Objects.requireNonNull(listingId, "Listing ID is required");
        this.sellerId = Objects.requireNonNull(sellerId, "Seller ID is required");
        this.description = description;
        this.expectedPrice = expectedPrice;
        this.deadline = deadline;
        this.status = AppraisalRequestStatus.OPEN;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void assign() {
        if (this.status != AppraisalRequestStatus.OPEN) {
            throw new IllegalStateException("Only OPEN requests can be assigned");
        }
        this.status = AppraisalRequestStatus.ASSIGNED;
    }

    public void start() {
        if (this.status != AppraisalRequestStatus.ASSIGNED) {
            throw new IllegalStateException("Only ASSIGNED requests can be started");
        }

        this.status = AppraisalRequestStatus.IN_PROGRESS;
    }

    public void complete() {
        if (this.status != AppraisalRequestStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only IN_PROGRESS requests can be completed");
        }

        this.status = AppraisalRequestStatus.COMPLETED;
    }

    public void cancel() {
        if (this.status == AppraisalRequestStatus.COMPLETED
                || this.status == AppraisalRequestStatus.CANCELLED
                || this.status == AppraisalRequestStatus.EXPIRED) {
            throw new IllegalStateException(
                    "Cannot cancel request in current status");
        }

        this.status = AppraisalRequestStatus.CANCELLED;
    }

    public void expire() {
        if (this.status == AppraisalRequestStatus.COMPLETED
                || this.status == AppraisalRequestStatus.CANCELLED
                || this.status == AppraisalRequestStatus.EXPIRED) {
            throw new IllegalStateException(
                    "Cannot expire request in current status");
        }

        this.status = AppraisalRequestStatus.EXPIRED;
    }

    // helpers
    public boolean isSeller(UUID userId) {
        return Objects.equals(this.sellerId, userId);
    }

    public boolean isOpen() {
        return this.status == AppraisalRequestStatus.OPEN;
    }
}
