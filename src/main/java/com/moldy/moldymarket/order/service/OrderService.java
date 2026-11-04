package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.request.OrderCreationRequest;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.mapper.OrderMapper;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.order.repository.OrderStatusHistoryRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService {
    OrderRepository orderRepository;
    OrderStatusHistoryRepository orderStatusHistoryRepository;
    CurrentUser currentUser;
    OrderMapper orderMapper;


    @Transactional
    public OrderResponse createOrder(OrderCreationRequest request){
        List<UUID> productIds = request.productIds().stream().distinct().toList();

        //todo: find all products matching with id list (in product repository)

        var user = currentUser.findCurrentUser();

        var order = orderMapper.toOrder(request);
        order.setBuyerId(user.getId());

        return orderMapper.toOrderResponse(orderRepository.save(order));
    }


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


    //todo: shipping simulator
}
