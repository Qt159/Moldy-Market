package com.moldy.moldymarket.order.dto.response;

import java.math.BigDecimal;

public record OrderAmounts (
        BigDecimal buyerPays, BigDecimal sellerReceives, BigDecimal commission
){
}
