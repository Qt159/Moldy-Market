package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.OrderResponseAssembler;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.cart.service.CartService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService {
    OrderRepository orderRepository;
    CurrentUser currentUser;
    OrderResponseAssembler orderResponses;
    CheckoutGroupRepository checkoutGroupRepository;
    PaymentRepository paymentRepository;
    ProductRepository productRepository;
    StoreRepository storeRepository;
    CartService cartService;

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrderByBuyerId(){
        var user = currentUser.findCurrentUser();
        var orders = orderRepository.findAllByBuyerId(user.getId());
        if(orders.isEmpty())
            throw new AppException(ErrorCode.ORDER_LIST_NOT_FOUND);
        return orderResponses.toResponses(orders);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId){
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        requireBuyer(order);
        return orderResponses.toResponse(order);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse receivedCOD(UUID orderId){
        var locked = lockOrder(orderId);
        var order = locked.order();
        if (locked.payment().getMethod() != PaymentMethod.COD) throw invalidState();
        if (order.getPaidAt() != null) return orderResponses.toResponse(order);
        if (order.getStatus() != OrderStatus.DELIVERED) throw new AppException(ErrorCode.ORDER_NOT_DELIVERED);
        if (locked.checkout().getStatus() != CheckoutGroupStatus.PAY_ON_DELIVERY
                || locked.payment().getStatus() != PaymentStatus.PENDING) throw invalidState();
        Instant now = Instant.now();
        var ids = productIds(List.of(order));
        for (UUID productId : ids) {
            productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND))
                    .completeReservationFor(locked.checkout().getId(), now);
        }
        order.confirmReceived();
        order.setPaidAt(now);
        updateCodGroup(locked.checkout(), locked.payment(), now);
        cartService.removePurchasedItems(order.getBuyerId(), ids);
        return orderResponses.toResponse(order);
    }

    @Transactional
    public OrderResponse confirmOrder(UUID orderId) {
        var locked = lockOrder(orderId);
        var order = locked.order();
        UUID userId = currentUser.findCurrentUser().getId();
        boolean isOwner = order.getStoreId() == null
                ? order.getSellerId().equals(userId)
                : storeRepository.findById(order.getStoreId())
                .map(store -> store.getOwnerId().equals(userId)).orElse(false);
        if (!isOwner) throw new AppException(ErrorCode.ORDER_NOT_EXISTED);
        if (locked.payment().getMethod() != PaymentMethod.COD) throw invalidState();
        if (order.getStatus() == OrderStatus.CONFIRMED) return orderResponses.toResponse(order);
        if (order.getStatus() != OrderStatus.PENDING_CONFIRMED) throw invalidState();
        order.setStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(Instant.now());
        return orderResponses.toResponse(order);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse startShipping(UUID orderId) {
        var order = lockOrder(orderId).order();
        if (order.getStatus() == OrderStatus.SHIPPING) return orderResponses.toResponse(order);
        if (order.getStatus() != OrderStatus.CONFIRMED) throw invalidState();
        order.setStatus(OrderStatus.SHIPPING);
        order.setShippedAt(Instant.now());
        return orderResponses.toResponse(order);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse markDelivered(UUID orderId) {
        var order = lockOrder(orderId).order();
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.COMPLETED)
            return orderResponses.toResponse(order);
        if (order.getStatus() != OrderStatus.SHIPPING) throw invalidState();
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now());
        return orderResponses.toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrderFromBuyer(UUID orderId) {
        var locked = lockOrder(orderId);
        var order = locked.order();
        requireBuyer(order);
        if (order.getStatus() == OrderStatus.CANCELLED) return orderResponses.toResponse(order);
        if (locked.payment().getStatus() != PaymentStatus.PENDING) throw invalidState();
        Instant now = Instant.now();
        List<Order> cancelled;
        if (locked.payment().getMethod() == PaymentMethod.ONLINE_BANKING) {
            if (locked.checkout().getStatus() != CheckoutGroupStatus.PENDING_PAYMENT
                    || locked.checkout().getOrders().stream().anyMatch(o -> o.getStatus() != OrderStatus.PENDING_PAYMENT))
                throw invalidState();
            // One bank QR pays the entire group; cancelling any unpaid order invalidates it all.
            cancelled = locked.checkout().getOrders();
        } else {
            if (locked.checkout().getStatus() != CheckoutGroupStatus.PAY_ON_DELIVERY
                    || (order.getStatus() != OrderStatus.PENDING_CONFIRMED && order.getStatus() != OrderStatus.CONFIRMED))
                throw invalidState();
            cancelled = List.of(order);
        }
        for (UUID productId : productIds(cancelled)) {
            productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND))
                    .releaseReservationFor(locked.checkout().getId(), now);
        }
        for (Order item : cancelled) {
            item.setStatus(OrderStatus.CANCELLED);
            item.setCancelledAt(now);
            item.setUpdatedAt(now);
        }
        if (locked.payment().getMethod() == PaymentMethod.ONLINE_BANKING) {
            locked.checkout().setStatus(CheckoutGroupStatus.CANCELLED);
            locked.payment().setStatus(PaymentStatus.FAILED);
            locked.payment().setUpdatedAt(now);
        } else {
            updateCodGroup(locked.checkout(), locked.payment(), now);
        }
        return orderResponses.toResponse(order);
    }

    private LockedOrder lockOrder(UUID orderId) {
        UUID checkoutId = orderRepository.findCheckoutGroupIdByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        var checkout = checkoutGroupRepository.findByIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_GROUP_NOT_EXISTED));
        var payment = paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));
        var order = checkout.getOrders().stream().filter(item -> orderId.equals(item.getId()))
                .findFirst().orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        return new LockedOrder(checkout, order, payment);
    }

    private void requireBuyer(Order order) {
        if (!order.getBuyerId().equals(currentUser.findCurrentUser().getId()))
            throw new AppException(ErrorCode.ORDER_NOT_EXISTED);
    }

    private List<UUID> productIds(List<Order> orders) {
        return orders.stream().flatMap(order -> order.getOrderItems().stream())
                .map(item -> item.getProductId()).distinct().sorted().toList();
    }

    private void updateCodGroup(CheckoutGroup checkout, Payment payment, Instant now) {
        var payable = checkout.getOrders().stream().filter(order -> order.getStatus() != OrderStatus.CANCELLED).toList();
        BigDecimal total = payable.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        checkout.setTotalAmount(total);
        payment.setAmount(total);
        payment.setUpdatedAt(now);
        if (payable.isEmpty()) {
            checkout.setStatus(CheckoutGroupStatus.CANCELLED);
            payment.setStatus(PaymentStatus.FAILED);
        } else if (payable.stream().allMatch(order -> order.getPaidAt() != null)) {
            checkout.markPaid();
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            payment.setPaidAt(payable.stream().map(Order::getPaidAt).max(Instant::compareTo).orElseThrow());
        }
    }

    private AppException invalidState() { return new AppException(ErrorCode.INVALID_ORDER_STATE); }
    private record LockedOrder(CheckoutGroup checkout, Order order, Payment payment) {}
}