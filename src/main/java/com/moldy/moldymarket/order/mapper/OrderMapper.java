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
    @Mapping(target = "checkoutGroupId", source = "checkoutGroup.id")
    @Mapping(target = "paymentMethod", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    OrderResponse toOrderResponse(Order order);


    @Mapping(target = "orderId", source = "order.id")
    OrderItemResponse toOrderItemResponse(OrderItem orderItem);

    @Mapping(target = "items", source = "orderItems")
    @Mapping(target = "checkoutGroupId", source = "checkoutGroup.id")

    OrderSummaryResponse toSummary(Order order);
}
