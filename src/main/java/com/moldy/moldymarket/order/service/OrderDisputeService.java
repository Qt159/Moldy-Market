package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.OrderRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderDisputeService {

    OrderRepository orderRepository;

    public record OrderDisputeInfo(UUID orderId, UUID buyerId, UUID sellerId,
                                   boolean delivered, Instant returnDeadline, BigDecimal totalAmount) {
    }

    @Transactional(readOnly = true)
    public OrderDisputeInfo getInfo(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        return new OrderDisputeInfo(order.getId(), order.getBuyerId(), order.getSellerId(),
                order.getStatus() == OrderStatus.DELIVERED,
                order.getReturnDeadline(), order.getTotalAmount());
    }

    @Transactional
    public void markDisputed(UUID orderId) {
        if (orderRepository.markDisputed(orderId, Instant.now()) != 1) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }
    }

    @Transactional
    public void revertDisputed(UUID orderId) {
        if (orderRepository.revertDisputed(orderId, Instant.now()) != 1) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }
    }

//    @Transactional
//    public void completeAfterRejectedDispute(UUID orderId) {
//        // It must (1) move DISPUTED -> COMPLETED and (2) release the seller's escrow.
//        orderService.completeDisputedOrder(orderId);
//    }
}