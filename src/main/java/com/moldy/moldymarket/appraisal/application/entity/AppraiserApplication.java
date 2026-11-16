
package com.moldy.moldymarket.appraisal.application.entity;

import com.moldy.moldymarket.appraisal.application.enums.AppraiserApplicationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table( name = "appraiser_applications",
        uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_application_request_appraiser",
            columnNames = {"appraisal_request_id", "appraiser_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppraiserApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appraisal_request_id", nullable = false)
    private UUID appraisalRequestId;

    @Column(name = "appraiser_id", nullable = false)
    private UUID appraiserId;

    @Column(name = "introduction", nullable = false, length = 1000)
    private String introduction;

    @Column(name = "experience_description", nullable = false, columnDefinition = "TEXT")
    private String experienceDescription;

    @Column(name = "proposed_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal proposedFee;

    @Column(name = "estimated_completion_hours", nullable = false)
    private Integer estimatedCompletionHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppraiserApplicationStatus status = AppraiserApplicationStatus.PENDING;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public AppraiserApplication(
            UUID appraisalRequestId,
            UUID appraiserId,
            String introduction,
            String experienceDescription,
            BigDecimal proposedFee,
            Integer estimatedCompletionHours) {
        this.appraisalRequestId = Objects.requireNonNull(
                appraisalRequestId, "Appraisal request ID is required");
        this.appraiserId = Objects.requireNonNull(
                appraiserId, "Appraiser ID is required");

        this.introduction = Objects.requireNonNull(
                introduction, "Introduction is required").trim();

        this.experienceDescription = Objects.requireNonNull(
                experienceDescription, "Experience description is required").trim();

        this.proposedFee = Objects.requireNonNull(
                proposedFee, "Proposed fee is required");

        this.estimatedCompletionHours = Objects.requireNonNull(
                estimatedCompletionHours, "Estimated completion hours is required");

        this.status = AppraiserApplicationStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.appliedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void accept() {
        validatePending();
        this.status = AppraiserApplicationStatus.ACCEPTED;
        this.respondedAt = Instant.now();
    }

    public void reject() {
        validatePending();
        this.status = AppraiserApplicationStatus.REJECTED;
        this.respondedAt = Instant.now();
    }

    public void withdraw() {
        validatePending();
        this.status = AppraiserApplicationStatus.WITHDRAWN;
        this.respondedAt = Instant.now();
    }

    public void expire() {
        validatePending();
        this.status = AppraiserApplicationStatus.EXPIRED;
        this.respondedAt = Instant.now();
    }

    private void validatePending() {
        if (this.status != AppraiserApplicationStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING applications can change status");
        }
    }

    public boolean isPending() {
        return this.status == AppraiserApplicationStatus.PENDING;
    }

    public boolean isOwnedBy(UUID appraiserId) {
        return Objects.equals(this.appraiserId, appraiserId);
    }
}
