
package com.moldy.moldymarket.appraisal.invitation.entity;

import com.moldy.moldymarket.appraisal.invitation.enums.AppraisalInvitationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "appraisal_invitations",
        uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_invitation_request_appraiser",
            columnNames = {"appraisal_request_id", "appraiser_id"})}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppraisalInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appraisal_request_id", nullable = false)
    private UUID appraisalRequestId;

    @Column(name = "appraiser_id", nullable = false)
    private UUID appraiserId;

    @Column(name = "proposed_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal proposedFee;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppraisalInvitationStatus status = AppraisalInvitationStatus.PENDING;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public AppraisalInvitation(
            UUID appraisalRequestId,
            UUID appraiserId,
            BigDecimal proposedFee,
            String message,
            Instant deadline) {
        this.appraisalRequestId = Objects.requireNonNull(
                appraisalRequestId, "Appraisal request ID is required");
        this.appraiserId = Objects.requireNonNull(
                appraiserId, "Appraiser ID is required");
        this.proposedFee = Objects.requireNonNull(
                proposedFee, "Proposed fee is required");
        this.message = Objects.requireNonNull(
                message, "Message is required").trim();
        this.deadline = Objects.requireNonNull(
                deadline, "Deadline is required");
        this.status = AppraisalInvitationStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;}

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();}

    public void accept() {
        validatePending();
        this.status = AppraisalInvitationStatus.ACCEPTED;
        this.respondedAt = Instant.now();}

    public void reject() {
        validatePending();
        this.status = AppraisalInvitationStatus.REJECTED;
        this.respondedAt = Instant.now();}

    public void cancel() {
        validatePending();
        this.status = AppraisalInvitationStatus.CANCELLED;
        this.respondedAt = Instant.now();}

    public void expire() {
        validatePending();
        this.status = AppraisalInvitationStatus.EXPIRED;
        this.respondedAt = Instant.now();}

    private void validatePending() {
        if (this.status != AppraisalInvitationStatus.PENDING) {
            throw new IllegalStateException("Only pending invitations can change status");}
    }

    public boolean isPending() {
        return this.status == AppraisalInvitationStatus.PENDING;
    }

    public boolean isOwnedBy(UUID appraiserId) {
        return Objects.equals(this.appraiserId, appraiserId);
    }
}
