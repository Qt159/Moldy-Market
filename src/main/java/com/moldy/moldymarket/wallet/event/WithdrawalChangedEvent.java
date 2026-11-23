package com.moldy.moldymarket.wallet.event;

import com.moldy.moldymarket.wallet.enums.WithdrawalStatus;

import java.util.UUID;

public record WithdrawalChangedEvent(
        Long withdrawalId,
        UUID userId,
        WithdrawalStatus status) {
}
