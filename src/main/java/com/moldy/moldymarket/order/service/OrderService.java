package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.OrderMapper;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService {
    OrderRepository orderRepository;
    CurrentUser currentUser;
    OrderMapper orderMapper;
    PaymentRepository paymentRepository;

    //buyers
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

        var user = currentUser.findCurrentUser();

        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if(!order.getBuyerId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);

        return orderMapper.toOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrderFromBuyer(UUID orderId){
        var user = currentUser.findCurrentUser();

        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if(!order.getBuyerId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);

        if(!(order.getStatus().equals(OrderStatus.CONFIRMED) || order.getStatus().equals(OrderStatus.PENDING_CONFIRMED)))
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());

        //todo: refund if buyers choose to pay by online_banking. For COD, there are nothing.
        return orderMapper.toOrderResponse(order);
    }

    //sellers
    @Transactional
    public OrderResponse confirmOrder(UUID orderId){
        var user = currentUser.findCurrentUser();
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        if(!order.getSellerId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        if(!order.getStatus().equals(OrderStatus.PENDING_CONFIRMED)) throw new AppException(ErrorCode.INVALID_ORDER_STATE);

        order.setStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(Instant.now());
        return orderMapper.toOrderResponse(orderRepository.save(order));
    }

    //admin
    @Transactional
    public OrderResponse startShipping(UUID orderId) {

        var order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if (!order.getStatus().equals(OrderStatus.CONFIRMED)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }

        order.setStatus(OrderStatus.SHIPPING);
        order.setShippedAt(Instant.now());

        return orderMapper.toOrderResponse(order);
    }

    @Transactional
    public OrderResponse markDelivered(UUID orderId) {

        var order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }

        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now());

        return orderMapper.toOrderResponse(order);
    }

    @Transactional
    public OrderResponse receivedCOD(UUID orderId){
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        var payment = paymentRepository.findByCheckoutGroupId(order.getCheckoutGroup().getId())
                        .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));

        if(!payment.getMethod().equals(PaymentMethod.COD)) throw new AppException(ErrorCode.INVALID_PAYMENT_METHOD);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_STATE);
        }
        order.confirmReceived();
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        return orderMapper.toOrderResponse(order);
    }



    //todo: shipping simulator
}
