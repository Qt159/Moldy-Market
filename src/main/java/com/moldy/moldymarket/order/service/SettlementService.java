package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.wallet.service.EscrowService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SettlementService {
    OrderRepository orderRepository;
    EscrowService escrowService;

    @Transactional
    public void settleOne(UUID orderId){
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if(order == null) return;

        if(!order.getStatus().equals(OrderStatus.COMPLETED)
                || order.getSettledAt() != null
                || order.getReturnDeadline() == null
                || !order.getReturnDeadline().isBefore(Instant.now())){
            return;
        }
        if(escrowService.settle(orderId)){
            order.markSettled();
        }
    }
}
