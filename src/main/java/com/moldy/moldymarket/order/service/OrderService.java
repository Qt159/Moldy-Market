package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.OrderMapper;
import com.moldy.moldymarket.order.repository.OrderRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService {
    OrderRepository orderRepository;
    CurrentUser currentUser;
    OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrderByBuyerId(){
        var user = currentUser.findCurrentUser();
        var orders = orderRepository.findAllByBuyerId(user.getId());
        if(orders.isEmpty())
            throw new AppException(ErrorCode.ORDER_LIST_NOT_FOUND);
        return orders.stream().map(orderMapper::toOrderResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId){
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        return orderMapper.toOrderResponse(order);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse receivedCOD(UUID orderId){
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        //order has not been delivered
        if(!order.getStatus().equals(OrderStatus.DELIVERED)) throw new AppException(ErrorCode.ORDER_NOT_DELIVERED);

        order.setStatus(OrderStatus.COMPLETED);
        return orderMapper.toOrderResponse(order);
    }

    //todo: shipping simulator
}
