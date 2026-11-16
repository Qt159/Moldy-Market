package com.moldy.moldymarket.wallet.entity;


import com.moldy.moldymarket.wallet.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "wallet_id", nullable = false)
    Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    BigDecimal amount;

    @Column(name = "available_balance_after", nullable = false, precision = 19, scale = 2)
    BigDecimal availableBalanceAfter;

    @Column(name = "held_balance_after", nullable = false, precision = 19, scale = 2, updatable = false)
    BigDecimal heldBalanceAfter;

    @Column(name = "reference_type", length = 30, updatable = false)
    String referenceType;

    @Column(name = "reference_id", updatable = false)
    String referenceId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    String idempotencyKey;

    @Column(length = 255)
    String description;

    @Column(name = "created_at", updatable = false)
    Instant createdAt = Instant.now();

    public static WalletTransaction create(Wallet wallet, TransactionType type, BigDecimal amount,
                                           String refType, String refId, String idempotencyKey, String description){
        return WalletTransaction.builder()
                .walletId(wallet.getId())
                .transactionType(type)
                .amount(amount)
                .availableBalanceAfter(wallet.getAvailableBalance())
                .heldBalanceAfter(wallet.getHeldBalance())
                .referenceType(refType)
                .referenceId(refId)
                .idempotencyKey(idempotencyKey)
                .description(description)
                .build();
    }
}
