package com.moldy.moldymarket.commission.entity;

import com.moldy.moldymarket.appraiser.enums.VerificationLevel;
import com.moldy.moldymarket.commission.enums.CommissionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "appraiser_commissions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_commission_assignment",
            columnNames = "assignment_id"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppraiserCommission {

    private static final BigDecimal ONE_HUNDRED =
        new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assignment_id", nullable = false, unique = true)
    private UUID assignmentId;

    @Column(name = "appraiser_id", nullable = false)
    private UUID appraiserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_level", nullable = false, length = 30)
    private VerificationLevel verificationLevel;

    @Column(name = "agreed_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal agreedFee;

    @Column(name = "appraiser_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal appraiserRate;

    @Column(name = "platform_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal platformRate;

    @Column(name = "appraiser_earnings", nullable = false, precision = 12, scale = 2)
    private BigDecimal appraiserEarnings;

    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommissionStatus status;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private Instant calculatedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    public AppraiserCommission(
        UUID assignmentId,
        UUID appraiserId,
        VerificationLevel verificationLevel,
        BigDecimal agreedFee
    ) {
        validateInput(
            assignmentId,
            appraiserId,
            verificationLevel,
            agreedFee
        );

        this.assignmentId = assignmentId;
        this.appraiserId = appraiserId;
        this.verificationLevel = verificationLevel;
        this.agreedFee = agreedFee;

        calculateCommission();

        this.status = CommissionStatus.EARNED;
    }

    private void calculateCommission() {

        switch (this.verificationLevel) {
            case BASIC -> {
                this.appraiserRate = new BigDecimal("70.00");
                this.platformRate = new BigDecimal("30.00");
            }

            case ADVANCED -> {
                this.appraiserRate = new BigDecimal("75.00");
                this.platformRate = new BigDecimal("25.00");
            }

            default -> throw new IllegalArgumentException(
                "Appraiser must have a verified level to earn commission"
            );
        }

        this.platformFee = this.agreedFee
            .multiply(this.platformRate)
            .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        // Đảm bảo tổng hai khoản luôn bằng agreedFee
        this.appraiserEarnings = this.agreedFee
            .subtract(this.platformFee);
    }

    public void markAsPaid() {
        if (this.status != CommissionStatus.EARNED) {
            throw new IllegalStateException(
                "Only earned commission can be marked as paid"
            );
        }

        this.status = CommissionStatus.PAID;
        this.paidAt = Instant.now();
    }

    public void cancel() {
        if (this.status != CommissionStatus.EARNED) {
            throw new IllegalStateException(
                "Only earned commission can be cancelled"
            );
        }

        this.status = CommissionStatus.CANCELLED;
    }

    private void validateInput(
        UUID assignmentId,
        UUID appraiserId,
        VerificationLevel verificationLevel,
        BigDecimal agreedFee
    ) {
        if (assignmentId == null) {
            throw new IllegalArgumentException(
                "Assignment ID is required"
            );
        }

        if (appraiserId == null) {
            throw new IllegalArgumentException(
                "Appraiser ID is required"
            );
        }

        if (verificationLevel == null) {
            throw new IllegalArgumentException(
                "Verification level is required"
            );
        }

        if (agreedFee == null ||
            agreedFee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Agreed fee must be greater than zero"
            );
        }
    }

    @PrePersist
    protected void onCreate() {
        this.calculatedAt = Instant.now();
    }
}