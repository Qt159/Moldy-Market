package com.moldy.moldymarket.commission.entity;

import com.moldy.moldymarket.commission.enums.CommissionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Appraiser Commission Entity
 * Financial tracking of appraiser earnings per appraisal workflow
 */
@Entity
@Table(
    name = "appraiser_commissions",
    indexes = {
        @Index(name = "idx_commission_appraiser", columnList = "appraiser_id"),
        @Index(name = "idx_commission_workflow", columnList = "workflow_id", unique = true),
        @Index(name = "idx_commission_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AppraiserCommission {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    /**
     * Reference to AppraisalWorkflow
     */
    @Column(name = "workflow_id", nullable = false, unique = true)
    UUID workflowId;

    /**
     * Appraiser who earned this commission
     */
    @Column(name = "appraiser_id", nullable = false)
    UUID appraiserId;

    // ===== Fee Breakdown =====

    /**
     * Fee agreed with seller
     */
    @Column(name = "agreed_fee", precision = 19, scale = 2, nullable = false)
    BigDecimal agreedFee;

    /**
     * Platform fee deducted
     */
    @Column(name = "platform_fee", precision = 19, scale = 2, nullable = false, columnDefinition = "DECIMAL(19,2) DEFAULT 0")
    BigDecimal platformFee = BigDecimal.ZERO;

    /**
     * Net earnings (agreed_fee - platform_fee)
     */
    @Column(name = "net_earnings", precision = 19, scale = 2, nullable = false)
    BigDecimal netEarnings;

    // ===== Status & Timeline =====

    /**
     * Commission status: PENDING, EARNED, PAID, CANCELLED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    CommissionStatus status = CommissionStatus.PENDING;

    /**
     * When commission was earned (workflow completed)
     */
    @Column(name = "earned_at")
    Instant earnedAt;

    /**
     * When commission was paid
     */
    @Column(name = "paid_at")
    Instant paidAt;

    // ===== Audit =====

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    // ===== Constructors =====

    public AppraiserCommission(UUID workflowId, UUID appraiserId, BigDecimal agreedFee) {
        this.workflowId = workflowId;
        this.appraiserId = appraiserId;
        this.agreedFee = agreedFee;
        this.platformFee = BigDecimal.ZERO;
        this.netEarnings = agreedFee;
        this.status = CommissionStatus.PENDING;
    }

    // ===== Status Transition Methods =====

    /**
     * Mark commission as earned (workflow completed)
     */
    public void markEarned(BigDecimal platformFee) {
        if (this.status != CommissionStatus.PENDING) {
            throw new IllegalStateException("Only PENDING commissions can be marked as earned");
        }
        this.platformFee = platformFee;
        this.netEarnings = this.agreedFee.subtract(platformFee);
        this.status = CommissionStatus.EARNED;
        this.earnedAt = Instant.now();
    }

    /**
     * Mark commission as paid
     */
    public void markPaid() {
        if (this.status != CommissionStatus.EARNED) {
            throw new IllegalStateException("Only EARNED commissions can be marked as paid");
        }
        this.status = CommissionStatus.PAID;
        this.paidAt = Instant.now();
    }

    /**
     * Cancel commission (workflow cancelled before completion)
     */
    public void cancel() {
        if (this.status == CommissionStatus.PAID) {
            throw new IllegalStateException("Cannot cancel paid commission");
        }
        this.status = CommissionStatus.CANCELLED;
    }

    // ===== JPA Lifecycle =====

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
}
