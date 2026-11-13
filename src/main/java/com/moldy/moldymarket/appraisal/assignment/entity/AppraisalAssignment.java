package com.moldy.moldymarket.appraisal.assignment.entity;

import com.moldy.moldymarket.appraisal.assignment.enums.AppraisalAssignmentStatus;
import com.moldy.moldymarket.appraisal.assignment.enums.AppraisalSourceType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "appraisal_assignments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_assignment_request",
            columnNames = "appraisal_request_id"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppraisalAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appraisal_request_id", nullable = false)
    private UUID appraisalRequestId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "appraiser_id", nullable = false)
    private UUID appraiserId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "invitation_id")
    private UUID invitationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private AppraisalSourceType sourceType;

    @Column(name = "agreed_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal agreedFee;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AppraisalAssignmentStatus status;
    @Column(name = "rejection_count", nullable = false)
    private Integer rejectionCount = 0;

    public void incrementRejectionCount() {
        this.rejectionCount++;}

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public AppraisalAssignment(
            UUID appraisalRequestId,
            UUID sellerId,
            UUID appraiserId,
            UUID applicationId,
            UUID invitationId,
            AppraisalSourceType sourceType,
            BigDecimal agreedFee
    ) {
        validateSource(applicationId, invitationId, sourceType);
        validateFee(agreedFee);

        this.appraisalRequestId = appraisalRequestId;
        this.sellerId = sellerId;
        this.appraiserId = appraiserId;
        this.applicationId = applicationId;
        this.invitationId = invitationId;
        this.sourceType = sourceType;
        this.agreedFee = agreedFee;
        this.status = AppraisalAssignmentStatus.ASSIGNED;

        this.assignedAt = Instant.now();
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

    public void assignConversation(UUID conversationId) {
        if (conversationId == null) {
            throw new IllegalArgumentException("Conversation ID cannot be null");
        }

        if (this.conversationId != null) {
            throw new IllegalStateException("Conversation has already been assigned");
        }

        this.conversationId = conversationId;
    }

    public void start() {
        if (this.status != AppraisalAssignmentStatus.ASSIGNED) {
            throw new IllegalStateException(
                "Only assigned appraisal can be started"
            );
        }

        this.status = AppraisalAssignmentStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    public void complete() {
        if (this.status != AppraisalAssignmentStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                "Only in-progress appraisal can be completed"
            );
        }

        this.status = AppraisalAssignmentStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        if (this.status == AppraisalAssignmentStatus.COMPLETED
                || this.status == AppraisalAssignmentStatus.CANCELLED) {
            throw new IllegalStateException(
                "This assignment cannot be cancelled"
            );
        }

        this.status = AppraisalAssignmentStatus.CANCELLED;
    }

    public boolean isOwnedBySeller(UUID sellerId) {
        return this.sellerId.equals(sellerId);
    }

    public boolean isAssignedToAppraiser(UUID appraiserId) {
        return this.appraiserId.equals(appraiserId);
    }

    private void validateSource(
            UUID applicationId,
            UUID invitationId,
            AppraisalSourceType sourceType
    ) {
        if (sourceType == null) {
            throw new IllegalArgumentException("Source type cannot be null");
        }

        if (sourceType == AppraisalSourceType.APPRAISER_APPLICATION) {
            if (applicationId == null || invitationId != null) {
                throw new IllegalArgumentException(
                    "Application assignment must have applicationId only"
                );
            }
        }

        if (sourceType == AppraisalSourceType.SELLER_INVITATION) {
            if (invitationId == null || applicationId != null) {
                throw new IllegalArgumentException(
                    "Invitation assignment must have invitationId only"
                );
            }
        }
    }

    private void validateFee(BigDecimal agreedFee) {
        if (agreedFee == null || agreedFee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Agreed fee must be greater than zero"
            );
        }
    }
}