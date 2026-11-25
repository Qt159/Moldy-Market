package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.cart.service.CartService;
import com.moldy.moldymarket.order.entity.*;
import com.moldy.moldymarket.order.enums.*;
import com.moldy.moldymarket.order.event.OrderStatusChangedEvent;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.*;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.product.entity.*;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.voucher.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutPaymentServiceTest {
    @Mock CheckoutGroupRepository checkouts;
    @Mock PaymentRepository payments;
    @Mock ProductRepository products;
    @Mock CartService carts;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock VoucherService voucherService;
    @InjectMocks CheckoutPaymentService service;
    UUID checkoutId=UUID.randomUUID(), buyerId=UUID.randomUUID(), productId=UUID.randomUUID();
    Instant paidAt=Instant.now(), deadline=paidAt.plusSeconds(60);
    CheckoutGroup checkout;
    Order order;
    Payment payment;
    Product product;

    @BeforeEach void setUp() {
        checkout=CheckoutGroup.builder().buyerId(buyerId).expiredAt(deadline).totalAmount(BigDecimal.TEN).build();
        ReflectionTestUtils.setField(checkout,"id",checkoutId);
        order=Order.builder().buyerId(buyerId).status(OrderStatus.PENDING_PAYMENT).build();
        order.addOrderItem(OrderItem.builder().productId(productId).quantity(1).build());
        checkout.addOrder(order);
        payment=Payment.builder().checkoutGroupId(checkoutId).payerId(buyerId).amount(BigDecimal.TEN)
                .method(PaymentMethod.ONLINE_BANKING).expiredAt(deadline).build();
        payment.setStatus(PaymentStatus.SUCCESSFUL); payment.setPaidAt(paidAt);
        product=new Product(UUID.randomUUID(),null,UUID.randomUUID(),"Book","Book",BigDecimal.TEN,
                ProductCondition.USED,PricingStrategy.SELLER_SET,List.of(),Map.of());
        product.reserveForCheckout(checkoutId,deadline,paidAt.minusSeconds(10));
        lenient().when(checkouts.findByIdForUpdate(checkoutId)).thenReturn(Optional.of(checkout));
        lenient().when(payments.findByCheckoutGroupIdForUpdate(checkoutId)).thenReturn(Optional.of(payment));
    }

    @Test void confirmsOnlineOrderSellsProductAndRemovesOnlyStoredItems() {
        when(products.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        service.confirmOnline(checkoutId);
        assertThat(checkout.getStatus()).isEqualTo(CheckoutGroupStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(paidAt);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD);
        assertThat(product.getReservedCheckoutId()).isNull();
        verify(carts).removePurchasedItems(buyerId,List.of(productId));
        verify(applicationEventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
        var locks=inOrder(checkouts,payments,products,carts);
        locks.verify(checkouts).findByIdForUpdate(checkoutId);
        locks.verify(payments).findByCheckoutGroupIdForUpdate(checkoutId);
        locks.verify(products).findByIdForUpdate(productId);
        locks.verify(carts).removePurchasedItems(buyerId,List.of(productId));
    }
    @Test void repeatedSuccessfulNotificationDoesNotRemoveTheCartTwice() {
        checkout.setStatus(CheckoutGroupStatus.PAID);
        service.confirmOnline(checkoutId);
        verifyNoInteractions(products,carts);
    }
    @Test void pendingPaymentCannotConfirmAnOrder() {
        payment.setStatus(PaymentStatus.PENDING);
        assertThatThrownBy(()->service.confirmOnline(checkoutId)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(products,carts);
    }
    @Test void codCannotUseTheOnlineListener() {
        payment.setMethod(PaymentMethod.COD);
        assertThatThrownBy(()->service.confirmOnline(checkoutId)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(products,carts);
    }
    @Test void aPaymentAtTheDeadlineCannotConsumeAReservation() {
        payment.setPaidAt(deadline);
        assertThatThrownBy(()->service.confirmOnline(checkoutId)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(products,carts);
    }
    @Test void cannotSellAProductHeldByANewerCheckout() {
        product.releaseReservationFor(checkoutId,paidAt);
        product.reserveForCheckout(UUID.randomUUID(),null,paidAt);
        when(products.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        assertThatThrownBy(()->service.confirmOnline(checkoutId)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(carts);
    }
    @Test void aCartFailureMustPropagateToRollBackThePaymentTransaction() {
        when(products.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        doThrow(new IllegalStateException("Cart write failed")).when(carts).removePurchasedItems(buyerId,List.of(productId));
        assertThatThrownBy(()->service.confirmOnline(checkoutId)).hasMessage("Cart write failed");
    }
    @Test void finalizesVoucherReservationWhenOnlineCheckoutUsesVoucher() {
        UUID voucherId = UUID.randomUUID();
        checkout.setVoucherId(voucherId);
        when(products.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        service.confirmOnline(checkoutId);
        verify(voucherService).finalizeReservation(voucherId);
    }
    @Test void preservesTheOrdinalEncodingOfExistingDatabaseRows() {
        assertThat(OrderStatus.CONFIRMED.ordinal()).isEqualTo(1);
        assertThat(OrderStatus.REFUNDED.ordinal()).isEqualTo(8);
        assertThat(OrderStatus.PENDING_CONFIRMED.ordinal()).isEqualTo(9);
        assertThat(CheckoutGroupStatus.PAID.ordinal()).isEqualTo(1);
        assertThat(CheckoutGroupStatus.PAY_ON_DELIVERY.ordinal()).isEqualTo(4);
    }
}
