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
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.product.service.ProductService;
import com.moldy.moldymarket.refund.enums.RefundReason;
import com.moldy.moldymarket.refund.event.RefundRequestEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
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
    ProductRepository productRepository;
    ProductService productService;
    ApplicationEventPublisher applicationEventPublisher;

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

        if(!order.getBuyerId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_NOT_EXISTED);

        return orderMapper.toOrderResponse(order);
    }

    //cancel if order hasn't shipped yet
    @Transactional
    public OrderResponse cancelOrderFromBuyer(UUID orderId){
        var user = currentUser.findCurrentUser();

        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if(!order.getBuyerId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_NOT_EXISTED);

        if(!(order.getStatus().equals(OrderStatus.CONFIRMED) || order.getStatus().equals(OrderStatus.PENDING_CONFIRMED)))
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());

        UUID groupId = order.getCheckoutGroup().getId();
        //release product reservation
        for(var item : order.getOrderItems()){
            productService.releaseProduct(item.getProductId(), groupId);
        }
        var payment = paymentRepository.findByCheckoutGroupId(order.getCheckoutGroup().getId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));


        //todo: refund if buyers choose to pay by online_banking. For COD, there is nothing.
        if(!payment.getMethod().equals(PaymentMethod.ONLINE_BANKING))
            applicationEventPublisher.publishEvent(new RefundRequestEvent(order.getId(), user.getId(), null, RefundReason.ORDER_CANCELLED, order.getTotalAmount()));

        return orderMapper.toOrderResponse(order);
    }

    //sellers
    @Transactional
    public OrderResponse confirmOrder(UUID orderId){
        var user = currentUser.findCurrentUser();
        var order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        if(!order.getSellerId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_NOT_EXISTED);
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
        var payment = paymentRepository.findByCheckoutGroupId(order.getCheckoutGroup().getId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));


        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now());
        if(payment.getMethod().equals(PaymentMethod.ONLINE_BANKING)) {
            order.confirmReceived();
        }
        return orderMapper.toOrderResponse(order);
    }

    @Transactional
    public OrderResponse receivedCOD(UUID orderId){
        var order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        var payment = paymentRepository.findByCheckoutGroupId(order.getCheckoutGroup().getId())
                        .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));

        if(!payment.getMethod().equals(PaymentMethod.COD)) throw new AppException(ErrorCode.INVALID_PAYMENT_METHOD);
        order.confirmReceived();
        for(var item : order.getOrderItems()){
            var product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
            product.markSold();
        }
        boolean othersOpen = orderRepository.existsByCheckoutGroupIdAndStatusIn(order.getCheckoutGroup().getId(), List.of(OrderStatus.PENDING_CONFIRMED, OrderStatus.CONFIRMED, OrderStatus.SHIPPING, OrderStatus.DELIVERED));
        if(!othersOpen && payment.getStatus() == PaymentStatus.PENDING){
            payment.setStatus(PaymentStatus.SUCCESSFUL);
        }

        return orderMapper.toOrderResponse(order);
    }
    @Transactional
    public void completeDisputeOrder(UUID orderId){
        var order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        order.completeDispute();
    }

    //todo: shipping simulator
}
