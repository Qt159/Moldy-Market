package com.moldy.moldymarket.wallet.dto.response;

import com.moldy.moldymarket.wallet.entity.WalletTransaction;
import com.moldy.moldymarket.wallet.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransactionResponse(
        Long id,
        TransactionType transactionType,
        BigDecimal amount,
        BigDecimal availableBalanceAfter,
        BigDecimal heldBalanceAfter,
        String referenceType,
        String referenceId,
        String description,
        Instant createdAt
) {
    public static WalletTransactionResponse from(WalletTransaction tx) {
        return new WalletTransactionResponse(
                tx.getId(),
                tx.getTransactionType(),
                tx.getAmount(),
                tx.getAvailableBalanceAfter(),
                tx.getHeldBalanceAfter(),
                tx.getReferenceType(),
                tx.getReferenceId(),
                tx.getDescription(),
                tx.getCreatedAt()
        );
    }
}
