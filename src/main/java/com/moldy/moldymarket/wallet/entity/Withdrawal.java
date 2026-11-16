package com.moldy.moldymarket.wallet.entity;

import com.moldy.moldymarket.wallet.enums.TransactionType;
import com.moldy.moldymarket.wallet.enums.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Withdrawal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "wallet_id", nullable = false)
    Long walletId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    BigDecimal amount;

    @Column(name = "bank_name", nullable = false, length = 100)
    String bankName;

    @Column(name = "bank_account_number", nullable = false, length = 50)
    String bankAccountNumber;

    @Column(name = "bank_account_holder", nullable = false, length = 150)
    String bankAccountHolder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    WithdrawalStatus status = WithdrawalStatus.REQUESTED;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    String idempotencyKey;

    @Column(name = "rejecttion_reason", length = 255)
    String rejectionReason;

    @Column(name = "requested_at", updatable = false)
    Instant requestedAt = Instant.now();

    @Column(name = "processed_at")
    Instant processedAt;


}
