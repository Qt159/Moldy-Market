package com.moldy.moldymarket.order.domain;

import java.util.UUID;

public record OrderGroupKey(
        UUID sellerId,
        UUID storeId
){ }
