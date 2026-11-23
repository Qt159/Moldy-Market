package com.moldy.moldymarket.wallet.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record EscrowHoldRequest (UUID orderId, UUID buyerId, UUID sellerId, BigDecimal amount, BigDecimal fee){
}
