package com.moldy.moldymarket.wallet.dto.response;

import com.moldy.moldymarket.wallet.enums.WithdrawalStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminWithdrawalResponse(
        Long id, BigDecimal amount, String bankName, String bankAccountNumber,
        String bankAccountHolder, WithdrawalStatus status, Instant requestedAt, Instant processedAt
) {}
