package com.moldy.moldymarket.cart.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;


public record CartItemsBySellerResponse(
        UUID sellerId,
        List<CartItemResponse> items,
        BigDecimal subtotalBySeller,
        Integer totalItemsBySeller
) {}
