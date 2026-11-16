package com.moldy.moldymarket.wallet.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record EscrowHoldRequest (String orderId, UUID buyerId, UUID sellerId, BigDecimal amount, BigDecimal fee){
}
