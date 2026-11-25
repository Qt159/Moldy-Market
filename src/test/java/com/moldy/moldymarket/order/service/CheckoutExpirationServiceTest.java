package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.entity.OrderItem;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.product.entity.PricingStrategy;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductCondition;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.voucher.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutExpirationServiceTest {
    @Mock CheckoutGroupRepository checkoutGroupRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ProductRepository productRepository;
    @Mock VoucherService voucherService;
    @InjectMocks CheckoutExpirationService service;

    private final UUID checkoutId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final Instant deadline = Instant.parse("2026-11-19T04:00:00Z");
    private CheckoutGroup checkout;
    private Payment payment;
    private Order order;

    @BeforeEach
    void setUp() {
        checkout = CheckoutGroup.builder().buyerId(UUID.randomUUID())
                .totalAmount(BigDecimal.TEN).expiredAt(deadline).build();
        ReflectionTestUtils.setField(checkout, "id", checkoutId);
        order = Order.builder().status(OrderStatus.PENDING_PAYMENT).build();
        order.addOrderItem(OrderItem.builder().productId(productId).quantity(1).build());
        checkout.addOrder(order);
        payment = Payment.builder().checkoutGroupId(checkoutId)
                .method(PaymentMethod.ONLINE_BANKING).expiredAt(deadline).build();
        when(checkoutGroupRepository.findByIdForUpdate(checkoutId)).thenReturn(Optional.of(checkout));
    }

    @Test
    void expiresAtTheDeadlineAndReleasesTheOwnedReservation() {
        Product product = reservedProduct(checkoutId, deadline.minusSeconds(900));
        when(paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)).thenReturn(Optional.of(payment));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        assertThat(service.expire(checkoutId, deadline)).isTrue();
        assertThat(checkout.getStatus()).isEqualTo(CheckoutGroupStatus.EXPIRED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order.getExpiredAt()).isEqualTo(deadline);
        assertThat(product.getReservedCheckoutId()).isNull();
        var locks = inOrder(checkoutGroupRepository, paymentRepository, productRepository);
        locks.verify(checkoutGroupRepository).findByIdForUpdate(checkoutId);
        locks.verify(paymentRepository).findByCheckoutGroupIdForUpdate(checkoutId);
        locks.verify(productRepository).findByIdForUpdate(productId);
    }

    @Test
    void doesNotExpireBeforeTheDeadline() {
        assertThat(service.expire(checkoutId, deadline.minusNanos(1))).isFalse();
        verifyNoInteractions(paymentRepository, productRepository);
    }

    @Test
    void doesNotExpireCodCheckouts() {
        checkout.setExpiredAt(null);
        assertThat(service.expire(checkoutId, deadline.plusSeconds(900))).isFalse();
        verifyNoInteractions(paymentRepository, productRepository);
    }

    @Test
    void leavesSuccessfulPaymentsAndTheirReservationsUntouched() {
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        when(paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)).thenReturn(Optional.of(payment));
        assertThat(service.expire(checkoutId, deadline)).isFalse();
        assertThat(checkout.getStatus()).isEqualTo(CheckoutGroupStatus.PENDING_PAYMENT);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verifyNoInteractions(productRepository);
    }

    @Test
    void anOldExpirationDoesNotReleaseAnotherCheckoutsReservation() {
        UUID newCheckoutId = UUID.randomUUID();
        Product product = reservedProduct(newCheckoutId, deadline);
        when(paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)).thenReturn(Optional.of(payment));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        assertThat(service.expire(checkoutId, deadline)).isTrue();
        assertThat(product.getReservedCheckoutId()).isEqualTo(newCheckoutId);
        assertThat(product.isReservedAt(deadline)).isTrue();
    }

    @Test
    void retryingExpirationDoesNotModifyTheCheckoutAgain() {
        checkout.setStatus(CheckoutGroupStatus.EXPIRED);
        assertThat(service.expire(checkoutId, deadline)).isFalse();
        verifyNoInteractions(paymentRepository, productRepository);
    }

    @Test
    void doesNotExpireOrdersThatHaveAlreadyProgressed() {
        order.setStatus(OrderStatus.SHIPPING);
        when(paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)).thenReturn(Optional.of(payment));
        assertThat(service.expire(checkoutId, deadline)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(productRepository);
    }

    @Test
    void releasesReservedVoucherWhenCheckoutExpires() {
        UUID voucherId = UUID.randomUUID();
        checkout.setVoucherId(voucherId);
        Product product = reservedProduct(checkoutId, deadline.minusSeconds(900));
        when(paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)).thenReturn(Optional.of(payment));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        assertThat(service.expire(checkoutId, deadline)).isTrue();
        verify(voucherService).releaseReservation(voucherId);
    }

    private Product reservedProduct(UUID ownerCheckoutId, Instant reservedAt) {
        Product product = new Product(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Used book", "Description", BigDecimal.TEN, ProductCondition.USED,
                PricingStrategy.SELLER_SET, List.of(), Map.of());
        product.reserveForCheckout(ownerCheckoutId, reservedAt.plusSeconds(900), reservedAt);
        return product;
    }
}
