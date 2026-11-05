package com.moldy.moldymarket.order.mapper;

import com.moldy.moldymarket.order.dto.request.OrderCreationRequest;
import com.moldy.moldymarket.order.dto.response.OrderItemResponse;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "orderItems", ignore = true)
    Order toOrder(OrderCreationRequest request);

    @Mapping(target = "items", source = "orderItems")
    OrderResponse toOrderResponse(Order order);


    OrderItemResponse toOrderItemResponse(OrderItem orderItem);

}
