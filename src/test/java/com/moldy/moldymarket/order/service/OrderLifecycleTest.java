package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.cart.service.CartService;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.entity.OrderItem;
import com.moldy.moldymarket.order.enums.*;
import com.moldy.moldymarket.order.event.OrderStatusChangedEvent;
import com.moldy.moldymarket.order.mapper.OrderResponseAssembler;
import com.moldy.moldymarket.order.repository.*;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.*;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.product.entity.*;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.store.entity.Store;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.user.entity.User;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleTest {
    @Mock OrderRepository orders;
    @Mock CheckoutGroupRepository checkouts;
    @Mock PaymentRepository payments;
    @Mock ProductRepository products;
    @Mock StoreRepository stores;
    @Mock CurrentUser currentUser;
    @Mock OrderResponseAssembler mapper;
    @Mock CartService carts;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @InjectMocks OrderService service;
    UUID checkoutId=UUID.randomUUID(), orderId=UUID.randomUUID(), buyerId=UUID.randomUUID(),
            productId=UUID.randomUUID(), ownerId=UUID.randomUUID(), storeId=UUID.randomUUID();
    CheckoutGroup checkout;
    Order order;
    Payment payment;
    Product product;
    User user;
    @BeforeEach void setup() {
        user=new User(); user.setId(buyerId);
        checkout=CheckoutGroup.builder().buyerId(buyerId).totalAmount(BigDecimal.TEN).build();
        checkout.setStatus(CheckoutGroupStatus.PAY_ON_DELIVERY);
        ReflectionTestUtils.setField(checkout,"id",checkoutId);
        order=order(orderId,productId,BigDecimal.TEN,OrderStatus.PENDING_CONFIRMED);
        payment=Payment.builder().checkoutGroupId(checkoutId).payerId(buyerId).amount(BigDecimal.TEN).method(PaymentMethod.COD).build();
        product=product(); product.reserveForCheckout(checkoutId,null,Instant.now());
        lenient().when(currentUser.findCurrentUser()).thenReturn(user);
        lenient().when(orders.findCheckoutGroupIdByOrderId(orderId)).thenReturn(Optional.of(checkoutId));
        lenient().when(checkouts.findByIdForUpdate(checkoutId)).thenReturn(Optional.of(checkout));
        lenient().when(payments.findByCheckoutGroupIdForUpdate(checkoutId)).thenReturn(Optional.of(payment));
        lenient().when(products.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
    }
    @Test void storeOwnerCanConfirmAListingPostedByStaff() {
        user.setId(ownerId);
        when(stores.findById(storeId)).thenReturn(Optional.of(new Store(ownerId,"Store","","")));
        service.confirmOrder(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isNotNull();
        verifyNoInteractions(carts);
    }
    @Test void listingStaffCannotConfirmInsteadOfTheStoreOwner() {
        user.setId(order.getSellerId());
        when(stores.findById(storeId)).thenReturn(Optional.of(new Store(ownerId,"Store","","")));
        assertThatThrownBy(()->service.confirmOrder(orderId)).isInstanceOf(RuntimeException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMED);
    }
    @Test void shippingRequiresConfirmationAndDeliveryRequiresShipping() {
        assertThatThrownBy(()->service.startShipping(orderId)).isInstanceOf(RuntimeException.class);
        order.setStatus(OrderStatus.CONFIRMED);
        service.startShipping(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(order.getShippedAt()).isNotNull();
        service.markDelivered(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isNotNull();
        verify(applicationEventPublisher, times(2)).publishEvent(any(OrderStatusChangedEvent.class));
        verifyNoInteractions(carts);
    }
    @Test void onlineBankingDeliveryAutomaticallyCompletesOrderWithReturnDeadline() {
        payment.setMethod(PaymentMethod.ONLINE_BANKING);
        order.setStatus(OrderStatus.SHIPPING);
        service.markDelivered(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getDeliveredAt()).isNotNull();
        assertThat(order.getCompletedAt()).isNotNull();
        assertThat(order.getReturnDeadline()).isAfter(order.getCompletedAt());
        verify(applicationEventPublisher).publishEvent(new OrderStatusChangedEvent(
                orderId, buyerId, order.getSellerId(), null, OrderStatus.SHIPPING, OrderStatus.COMPLETED));
    }
    @Test void codCollectionCompletesOnlyAfterDeliveryAndDeletesOnlyItsItems() {
        order.setStatus(OrderStatus.DELIVERED);
        service.receivedCOD(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
        assertThat(payment.getPaidAt()).isEqualTo(order.getPaidAt());
        assertThat(checkout.getStatus()).isEqualTo(CheckoutGroupStatus.PAID);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD);
        verify(carts).removePurchasedItems(buyerId,List.of(productId));
    }
    @Test void repeatedCollectionCannotConsumeOrRemoveItemsAgain() {
        order.setStatus(OrderStatus.COMPLETED); order.setPaidAt(Instant.now());
        payment.setStatus(PaymentStatus.SUCCESSFUL); checkout.setStatus(CheckoutGroupStatus.PAID);
        service.receivedCOD(orderId);
        verifyNoInteractions(products,carts);
    }
    @Test void onlineOrderCannotBeCollectedAsCod() {
        payment.setMethod(PaymentMethod.ONLINE_BANKING); order.setStatus(OrderStatus.DELIVERED);
        assertThatThrownBy(()->service.receivedCOD(orderId)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(carts);
    }
    @Test void partialCodCollectionKeepsTheOtherOrderAndGroupPending() {
        order(UUID.randomUUID(),UUID.randomUUID(),BigDecimal.ONE,OrderStatus.PENDING_CONFIRMED);
        order.setStatus(OrderStatus.DELIVERED);
        service.receivedCOD(orderId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(checkout.getStatus()).isEqualTo(CheckoutGroupStatus.PAY_ON_DELIVERY);
        verify(carts).removePurchasedItems(buyerId,List.of(productId));
    }
    @Test void cancellingUncollectedCodReleasesTheHoldWithoutRemovingCartItems() {
        service.cancelOrderFromBuyer(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getReservedCheckoutId()).isNull();
        assertThat(checkout.getStatus()).isEqualTo(CheckoutGroupStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verifyNoInteractions(carts);
    }
    @Test void cancellingOneCodOrderAdjustsTheRemainingPayableAmount() {
        var other=order(UUID.randomUUID(),UUID.randomUUID(),BigDecimal.ONE,OrderStatus.PENDING_CONFIRMED);
        service.cancelOrderFromBuyer(orderId);
        assertThat(other.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMED);
        assertThat(checkout.getStatus()).isEqualTo(CheckoutGroupStatus.PAY_ON_DELIVERY);
        assertThat(checkout.getTotalAmount()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.ONE);
        verifyNoInteractions(carts);
    }
    @Test void cancellingTheLastUnpaidCodOrderAfterPartialCollectionClosesTheGroup() {
        var other=order(UUID.randomUUID(),UUID.randomUUID(),BigDecimal.ONE,OrderStatus.COMPLETED);
        var collectedAt=Instant.now().minusSeconds(10); other.setPaidAt(collectedAt);
        service.cancelOrderFromBuyer(orderId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESSFUL);
        assertThat(payment.getPaidAt()).isEqualTo(collectedAt);
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(checkout.getStatus()).isEqualTo(CheckoutGroupStatus.PAID);
    }
    @Test void shippingOrdersCannotBeCancelled() {
        order.setStatus(OrderStatus.SHIPPING);
        assertThatThrownBy(()->service.cancelOrderFromBuyer(orderId)).isInstanceOf(RuntimeException.class);
        assertThat(product.getReservedCheckoutId()).isEqualTo(checkoutId);
    }
    @Test void cancellingUnpaidBankOrderCancelsTheWholeSharedQrCheckout() {
        checkout.setStatus(CheckoutGroupStatus.PENDING_PAYMENT);
        payment.setMethod(PaymentMethod.ONLINE_BANKING); order.setStatus(OrderStatus.PENDING_PAYMENT);
        var other=order(UUID.randomUUID(),UUID.randomUUID(),BigDecimal.ONE,OrderStatus.PENDING_PAYMENT);
        var otherProduct=product(); otherProduct.reserveForCheckout(checkoutId,null,Instant.now());
        when(products.findByIdForUpdate(other.getOrderItems().get(0).getProductId())).thenReturn(Optional.of(otherProduct));
        service.cancelOrderFromBuyer(orderId);
        assertThat(checkout.getOrders()).allMatch(o->o.getStatus()==OrderStatus.CANCELLED);
        assertThat(checkout.getStatus()).isEqualTo(CheckoutGroupStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(product.getReservedCheckoutId()).isNull();
        assertThat(otherProduct.getReservedCheckoutId()).isNull();
        verifyNoInteractions(carts);
    }
    @Test void paidOnlineOrdersUseASeparateRefundFlowAndCannotBeCancelledHere() {
        checkout.setStatus(CheckoutGroupStatus.PAID); payment.setMethod(PaymentMethod.ONLINE_BANKING);
        payment.setStatus(PaymentStatus.SUCCESSFUL); order.setStatus(OrderStatus.CONFIRMED);
        assertThatThrownBy(()->service.cancelOrderFromBuyer(orderId)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(products,carts);
    }
    @Test void anotherBuyerCannotCancelTheOrder() {
        user.setId(UUID.randomUUID());
        assertThatThrownBy(()->service.cancelOrderFromBuyer(orderId)).isInstanceOf(RuntimeException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMED);
    }
    private Order order(UUID id,UUID listingId,BigDecimal amount,OrderStatus status) {
        var value=Order.builder().buyerId(buyerId).sellerId(UUID.randomUUID()).storeId(storeId).totalAmount(amount).status(status).build();
        ReflectionTestUtils.setField(value,"id",id);
        value.addOrderItem(OrderItem.builder().productId(listingId).quantity(1).build());
        checkout.addOrder(value);
        return value;
    }
    private Product product() {
        return new Product(UUID.randomUUID(),storeId,UUID.randomUUID(),"Book","Book",BigDecimal.TEN,
                ProductCondition.USED,PricingStrategy.SELLER_SET,List.of(),Map.of());
    }
}
