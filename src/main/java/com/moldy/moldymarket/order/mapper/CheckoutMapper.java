package com.moldy.moldymarket.order.mapper;

import com.moldy.moldymarket.order.dto.response.CheckoutResponse;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CheckoutMapper {
    CheckoutResponse toResponse(CheckoutGroup checkoutGroup);
}
