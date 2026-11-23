package com.moldy.moldymarket.refund.entity;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.refund.enums.RefundDestination;
import com.moldy.moldymarket.refund.enums.RefundReason;
import com.moldy.moldymarket.refund.enums.RefundStatus;
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
@Table(name = "refunds", uniqueConstraints = @UniqueConstraint(name="uq_refund_order", columnNames = "order_id"))
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefundRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    UUID orderId;

    @Column(name = "dispute_id", updatable = false)
    UUID disputeId;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    UUID buyerId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination", nullable = false, updatable = false)
    RefundDestination destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    RefundStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false)
    RefundReason reason;

    String bankName;
    String bankAccountNumber;
    UUID reviewedBy;
    Instant reviewedAt;
    String transferReference;
    String evidenceUrl;
    String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
    @Column(name = "processed_at")
    Instant processedAt;

    @Version
    Long version;

    @Builder
    private RefundRequest(UUID orderId, UUID buyerId, UUID disputeId, BigDecimal amount, RefundDestination destination, RefundReason reason){
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.disputeId = disputeId;
        this.amount = amount;
        this.reason = reason;
        this.destination = destination;
        this.status = RefundStatus.PENDING_CONFIRMATION;
        this.processedAt = Instant.now();
    }

    public void snapshotBank(String bankName, String bankAccountNumber){
        if(destination != RefundDestination.BANK || status != RefundStatus.PENDING_CONFIRMATION)
            throw new AppException(ErrorCode.INVALID_REFUND_STATE);
        this.bankName = bankName;
        this.bankAccountNumber = bankAccountNumber;
    }

    public void confirm(UUID adminId){
        if(!this.status.equals(RefundStatus.PENDING_CONFIRMATION)) throw new AppException(ErrorCode.INVALID_REFUND_STATE);
        this.status = RefundStatus.CONFIRMED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
        this.processedAt = Instant.now();
    }

    public void completeToWallet(){
        if(!this.status.equals(RefundStatus.CONFIRMED)) throw new AppException(ErrorCode.INVALID_REFUND_STATE);
        if(!this.destination.equals(RefundDestination.WALLET)) throw new AppException(ErrorCode.INVALID_REFUND_STATE);
        this.status = RefundStatus.COMPLETED;
        this.processedAt = Instant.now();
    }


    @PrePersist
    void onCreate(){
        this.createdAt = Instant.now();
    }

    public void complete(){
        if(!this.status.equals(RefundStatus.PENDING_CONFIRMATION))
            throw new AppException(ErrorCode.INVALID_REFUND_STATE);

        this.status = RefundStatus.COMPLETED;
        this.processedAt = Instant.now();
    }

}
