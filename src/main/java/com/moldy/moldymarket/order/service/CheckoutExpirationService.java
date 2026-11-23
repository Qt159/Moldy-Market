package com.moldy.moldymarket.order.service;

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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutExpirationService {

    private final CheckoutGroupRepository checkoutGroupRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;

    @Transactional
    public boolean expire(UUID checkoutId, Instant now) {
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(now, "now");

        var checkout = checkoutGroupRepository.findByIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_GROUP_NOT_EXISTED));

        if (checkout.getStatus() != CheckoutGroupStatus.PENDING_PAYMENT
                || checkout.getExpiredAt() == null
                || checkout.getExpiredAt().isAfter(now)) {
            return false;
        }

        var payment = paymentRepository
                .findByCheckoutGroupIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));

        if (payment.getMethod() != PaymentMethod.ONLINE_BANKING
                || payment.getStatus() != PaymentStatus.PENDING) {
            return false;
        }

        boolean hasProgressedOrder = checkout.getOrders().stream()
                .anyMatch(order -> order.getStatus() != OrderStatus.PENDING_PAYMENT);

        if (hasProgressedOrder) {
            return false;
        }

        var productIds = checkout.getOrders().stream()
                .flatMap(order -> order.getOrderItems().stream())
                .map(item -> item.getProductId())
                .distinct()
                .sorted()
                .toList();

        for (UUID productId : productIds) {
            var product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

            product.releaseReservationFor(checkoutId, now);
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setUpdatedAt(now);

        for (var order : checkout.getOrders()) {
            order.setStatus(OrderStatus.EXPIRED);
            order.setExpiredAt(now);
            order.setUpdatedAt(now);
        }

        checkout.markExpired();
        return true;
    }
}
