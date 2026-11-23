package com.moldy.moldymarket.order.mapper;

import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderResponseAssembler {
    private final OrderMapper orderMapper;
    private final PaymentRepository paymentRepository;

    public OrderResponse toResponse(Order order) {
        return toResponses(List.of(order)).get(0);
    }

    public List<OrderResponse> toResponses(List<Order> orders) {
        var checkoutIds = orders.stream().map(this::checkoutId)
                .filter(Objects::nonNull).distinct().toList();
        Map<UUID, Payment> payments = checkoutIds.isEmpty() ? Map.of()
                : paymentRepository.findAllByCheckoutGroupIdIn(checkoutIds).stream()
                .collect(Collectors.toMap(Payment::getCheckoutGroupId, Function.identity()));
        return orders.stream().map(order -> {
            UUID checkoutId = checkoutId(order);
            return enrich(order, checkoutId == null ? null : payments.get(checkoutId));
        }).toList();
    }

    private OrderResponse enrich(Order order, Payment payment) {
        var response = orderMapper.toOrderResponse(order);
        return new OrderResponse(
                response.id(), response.orderCode(), response.checkoutGroupId(),
                response.buyerId(), response.sellerId(), response.status(), response.storeId(),
                response.totalAmount(), response.receiverName(), response.receiverPhone(),
                response.shippingFee(), response.shippingAddress(), response.shopDiscount(),
                response.platformDiscount(), response.confirmedAt(), response.shippedAt(),
                response.deliveredAt(), response.returnDeadline(), response.completedAt(),
                response.cancelledAt(), response.createdAt(), response.items(),
                payment == null ? null : payment.getMethod(), paymentStatus(order, payment),
                order.getPaidAt());
    }

    private PaymentStatus paymentStatus(Order order, Payment payment) {
        if (payment == null || payment.getMethod() == null) return null;
        // COD is collected per order; the shared payment may still be pending for other orders.
        if (order.getPaidAt() != null) return PaymentStatus.SUCCESSFUL;
        if (payment.getMethod() == PaymentMethod.COD) {
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED)
                return PaymentStatus.FAILED;
            return PaymentStatus.PENDING;
        }
        return payment.getStatus();
    }

    private UUID checkoutId(Order order) {
        return order.getCheckoutGroup() == null ? null : order.getCheckoutGroup().getId();
    }
}
