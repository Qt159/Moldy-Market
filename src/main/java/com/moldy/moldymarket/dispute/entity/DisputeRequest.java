package com.moldy.moldymarket.dispute.entity;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.dispute.enums.DisputeReason;
import com.moldy.moldymarket.dispute.enums.DisputeStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "dispute_requests",
        uniqueConstraints = @UniqueConstraint(name = "uq_dispute_order", columnNames = "order_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DisputeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    UUID id;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    UUID buyerId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    UUID sellerId;

    @Column(name = "order_id", nullable = false, updatable = false)
    UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    DisputeReason reason;

    @Column(name = "description", nullable = false, length = 2000)
    String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    DisputeStatus status = DisputeStatus.OPEN;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "dispute_images", joinColumns = @JoinColumn(name = "dispute_id"))
    @Column(name = "image_url", nullable = false, length = 1000)
    List<String> imageUrls = new ArrayList<>();

    @Column(name = "reviewed_by")
    UUID reviewedBy;

    @Column(name = "reviewed_at")
    Instant reviewedAt;

    @Column(name = "decision_note", length = 1000)
    String decisionNote;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Version
    Long version;

    @Builder
    private DisputeRequest(UUID buyerId, UUID sellerId, UUID orderId,
                           DisputeReason reason, String description, List<String> imageUrls) {
        if (buyerId == null || sellerId == null || orderId == null || reason == null
                || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Dispute is missing required fields");
        }
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.orderId = orderId;
        this.reason = reason;
        this.description = description;
        if (imageUrls != null) {
            this.imageUrls = new ArrayList<>(imageUrls);
        }
    }

    public boolean isOpen() {
        return status == DisputeStatus.OPEN;
    }
    private boolean isPendingApproval() {
        return status == DisputeStatus.PENDING_APPROVAL;
    }

    public void approve(UUID adminId, String note) {
        assertOpenAndPendingApproval();
        this.status = DisputeStatus.APPROVED;
        review(adminId, note);
    }

    public void reject(UUID adminId, String note) {
        assertOpenAndPendingApproval();
        this.status = DisputeStatus.REJECTED;
        review(adminId, note);
    }

    public void reopen(DisputeReason reason, String description, List<String> imageUrls){
        if (this.status != DisputeStatus.CANCELLED) {
            throw new IllegalStateException("Only a cancelled dispute can be reopened");
        }
        this.status = DisputeStatus.OPEN;
        this.reason = reason;
        this.description = description;
        this.imageUrls.clear();
        if(imageUrls != null) this.imageUrls.addAll(imageUrls);
    }

    public void cancel() {
        this.status = DisputeStatus.CANCELLED;
    }

    private void review(UUID adminId, String note) {
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
        this.decisionNote = note;
    }

    private void assertOpenAndPendingApproval() {
        if (!isOpen() && !isPendingApproval()) {
            throw new IllegalStateException("Dispute is not open: " + status);
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
