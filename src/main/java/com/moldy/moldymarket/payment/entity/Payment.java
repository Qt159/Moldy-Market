package com.moldy.moldymarket.payment.entity;

import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_status_expires", columnList = "status, expires_at"),
        @Index(name = "idx_payments_checkout_group", columnList = "checkout_group_id")})
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    UUID id;

    @Column(name = "checkout_group_id", nullable = false, updatable = false)
    UUID checkoutGroupId;

    @Column(name = "payer_id", nullable = false, updatable = false)
    UUID payerId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false)
    PaymentMethod method;

    @Column(name = "qr_bank_url", updatable = false)
    String qrBankUrl;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_provider_id", unique = true)
    PaymentTransaction paymentTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "expired_at", updatable = false)
    Instant expiredAt;

    @Column(name = "paid_at")
    Instant paidAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "payment_code", updatable = false, unique = true)
    @Setter(AccessLevel.NONE)
    String paymentCode;

    @Version
    @Setter(AccessLevel.NONE)
    Long version;

    @Builder
    public Payment(UUID checkoutGroupId, UUID payerId, BigDecimal amount,
                   PaymentMethod method, String paymentCode, String qrBankUrl,
                   Instant expiredAt){
        this.checkoutGroupId = checkoutGroupId;
        this.payerId = payerId;
        this.amount = amount;
        this.method = method;
        this.paymentCode = paymentCode;
        this.qrBankUrl = qrBankUrl;
        this.expiredAt = expiredAt;

    }

    @PrePersist
    public void onCreate(){
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = PaymentStatus.PENDING;
    }
    @PreUpdate
    public void onUpdate(){
        this.updatedAt = Instant.now();
    }
}
