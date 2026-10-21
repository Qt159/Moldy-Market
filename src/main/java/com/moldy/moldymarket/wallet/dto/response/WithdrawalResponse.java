package com.moldy.moldymarket.wallet.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record WithdrawalResponse(
        Long id,
        Long walletId,
        BigDecimal amount,
        String bankName,
        String maskedBankAccountNumber,
        String bankAccountHolder,
        String status,
        String rejectionReason,
        Instant requestedAt,
        Instant processedAt
) {}
