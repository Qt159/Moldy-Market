package com.moldy.moldymarket.wallet.dto.response;


import com.moldy.moldymarket.wallet.enums.WalletStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(
        UUID userId,
        BigDecimal availableBalance,
        BigDecimal heldBalance,
        BigDecimal pendingWithdrawBalance,
        WalletStatus status){
}
