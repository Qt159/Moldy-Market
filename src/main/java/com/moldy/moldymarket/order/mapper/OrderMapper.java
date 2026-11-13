package com.moldy.moldymarket.order.mapper;

import com.moldy.moldymarket.order.dto.response.OrderItemResponse;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.dto.response.OrderSummaryResponse;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "items", source = "orderItems")
    OrderResponse toOrderResponse(Order order);


    OrderItemResponse toOrderItemResponse(OrderItem orderItem);

    @Mapping(target = "items", source = "orderItems")

    OrderSummaryResponse toSummary(Order order);
}
