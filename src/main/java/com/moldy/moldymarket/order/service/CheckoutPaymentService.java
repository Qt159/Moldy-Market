package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.cart.service.CartService;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutPaymentService {
    private final CheckoutGroupRepository checkoutGroupRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmOnline(UUID checkoutId) {
        var checkout = checkoutGroupRepository.findByIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_GROUP_NOT_EXISTED));
        var payment = paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));
        if (payment.getMethod() != PaymentMethod.ONLINE_BANKING
                || payment.getStatus() != PaymentStatus.SUCCESSFUL) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }
        if (checkout.getStatus() == CheckoutGroupStatus.PAID) return;
        var paidAt = payment.getPaidAt();
        if (checkout.getStatus() != CheckoutGroupStatus.PENDING_PAYMENT || paidAt == null
                || checkout.getExpiredAt() == null || !paidAt.isBefore(checkout.getExpiredAt())
                || !checkout.getBuyerId().equals(payment.getPayerId())
                || checkout.getTotalAmount().compareTo(payment.getAmount()) != 0
                || checkout.getOrders().isEmpty()
                || checkout.getOrders().stream().anyMatch(order -> order.getStatus() != OrderStatus.PENDING_PAYMENT)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }
        var productIds = checkout.getOrders().stream().flatMap(order -> order.getOrderItems().stream())
                .map(item -> item.getProductId()).distinct().sorted().toList();
        for (UUID productId : productIds) {
            var product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
            product.completeReservationFor(checkoutId, paidAt);
        }
        for (var order : checkout.getOrders()) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaidAt(paidAt);
            order.setConfirmedAt(paidAt);
            order.setUpdatedAt(paidAt);
        }
        checkout.markPaid();
        cartService.removePurchasedItems(checkout.getBuyerId(), productIds);
    }
}
