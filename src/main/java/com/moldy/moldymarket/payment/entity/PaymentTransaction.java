package com.moldy.moldymarket.payment.entity;

import com.moldy.moldymarket.payment.enums.MatchStatus;
import com.moldy.moldymarket.payment.enums.TransferType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@Builder
@Entity
@NoArgsConstructor
@Table(name = "payment_transactions", uniqueConstraints = @UniqueConstraint(name = "uq_provider_ref", columnNames = {"provider", "external_id"}))
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    UUID id;

    @Column(name = "provider", nullable = false, updatable = false)
    String provider;


    @Column(name = "external_id", nullable = false, updatable = false, length = 64)
    String externalId;


    @Column(name = "gateway", nullable = false, updatable = false)
    String gateway;

    @Column(name = "transaction_date", nullable = false, updatable = false)
    Instant transactionDate;

    @Column(name = "account_number", nullable = false, updatable = false)
    String accountNumber;

    String subAccount;
    String paymentCode;

    @Column(name = "content", nullable = false, updatable = false)
    String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, updatable = false)
    TransferType transferType;

    String description;

    @Column(name = "transfer_amount", nullable = false, updatable = false)
    BigDecimal transferAmount;

    @Column(name = "reference_code", updatable = false)
    String referenceCode;


    @Column(name = "match_status", nullable = false)
    MatchStatus matchStatus = MatchStatus.UNMATCHED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb", updatable = false)
    String rawPayload;

    @Column(name = "received_at", nullable = false, updatable = false)
    Instant receivedAt;

    public void recordMatch(String paymentCode, MatchStatus status){
        this.paymentCode = paymentCode;
        this.matchStatus = status;
    }

    @PrePersist
    void onCreate(){
        this.receivedAt = Instant.now();
    }
}
