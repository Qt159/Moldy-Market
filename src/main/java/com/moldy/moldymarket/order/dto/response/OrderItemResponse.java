package com.moldy.moldymarket.order.dto.response;


import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse (
        Long id,
        UUID orderId,
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        String imageUrl){
}
