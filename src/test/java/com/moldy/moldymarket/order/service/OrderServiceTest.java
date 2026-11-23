package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.OrderResponseAssembler;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.entity.OrderItem;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.product.entity.*;
import com.moldy.moldymarket.cart.service.CartService;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock CheckoutGroupRepository checkoutRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ProductRepository productRepository;
    @Mock CartService cartService;
    @Mock StoreRepository storeRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrderResponseAssembler orderMapper;

    @InjectMocks
    private OrderService orderService;

    private UUID buyerId;
    private UUID orderId;
    private User buyer;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        buyerId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        buyer = new User();
        buyer.setId(buyerId);
        lenient().when(currentUser.findCurrentUser()).thenReturn(buyer);

        sampleOrder = Order.builder()
                .buyerId(buyerId)
                .sellerId(UUID.randomUUID())
                .orderCode("ORD-TEST01")
                .status(OrderStatus.DELIVERED)
                .receiverName("Test Buyer")
                .receiverPhone("0901234567")
                .shippingAddress("123 Test Street")
                .shippingFee(BigDecimal.valueOf(30000))
                .totalAmount(BigDecimal.valueOf(500000))
                .build();
        ReflectionTestUtils.setField(sampleOrder, "id", orderId);
    }

    @Test
    @DisplayName("should return all orders for current buyer when orders exist")
    void should_getAllOrderByBuyerId_when_ordersExist() {
        // Arrange
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        when(orderRepository.findAllByBuyerId(buyerId)).thenReturn(List.of(sampleOrder));
        when(orderMapper.toResponses(List.of(sampleOrder))).thenReturn(List.of(mock(OrderResponse.class)));

        // Act
        List<OrderResponse> result = orderService.getAllOrderByBuyerId();

        // Assert
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("should throw ORDER_LIST_NOT_FOUND when current buyer has no orders")
    void should_throwOrderListNotFound_when_buyerHasNoOrders() {
        // Arrange
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        when(orderRepository.findAllByBuyerId(buyerId)).thenReturn(Collections.emptyList());

        // Act & Assert
        assertThatThrownBy(() -> orderService.getAllOrderByBuyerId())
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_LIST_NOT_FOUND);
    }

    @Test
    @DisplayName("should return order by id when order exists")
    void should_getOrderById_when_orderExists() {
        // Arrange
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(orderMapper.toResponse(sampleOrder)).thenReturn(mock(OrderResponse.class));

        // Act
        OrderResponse response = orderService.getOrderById(orderId);

        // Assert
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("should throw ORDER_NOT_EXISTED when order does not exist")
    void should_throwOrderNotExisted_when_orderNotFound() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.getOrderById(unknownId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_EXISTED);
    }

    @Test
    @DisplayName("should mark order as COMPLETED when received COD on DELIVERED order")
    void should_completeOrder_when_receivedCodOnDeliveredOrder() {
        // Arrange
        stubCodCheckout();
        when(orderMapper.toResponse(sampleOrder)).thenReturn(mock(OrderResponse.class));

        // Act
        OrderResponse response = orderService.receivedCOD(orderId);

        // Assert
        assertThat(sampleOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("should throw ORDER_NOT_DELIVERED when receiving COD on non-DELIVERED order")
    void should_throwOrderNotDelivered_when_orderStatusIsNotDelivered() {
        // Arrange
        sampleOrder.setStatus(OrderStatus.CONFIRMED);
        stubCodCheckout();

        // Act & Assert
        assertThatThrownBy(() -> orderService.receivedCOD(orderId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_DELIVERED);
    }
    @Test
    void anotherBuyerCannotReadTheOrderByGuessingItsId() {
        buyer.setId(UUID.randomUUID());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));
        assertThatThrownBy(() -> orderService.getOrderById(orderId))
                .hasFieldOrPropertyWithValue("errorCode",ErrorCode.ORDER_NOT_EXISTED);
    }

    private void stubCodCheckout() {
        UUID checkoutId=UUID.randomUUID(), productId=UUID.randomUUID();
        var checkout=CheckoutGroup.builder().buyerId(buyerId).totalAmount(sampleOrder.getTotalAmount()).build();
        ReflectionTestUtils.setField(checkout,"id",checkoutId);
        checkout.setStatus(CheckoutGroupStatus.PAY_ON_DELIVERY);
        checkout.addOrder(sampleOrder);
        sampleOrder.addOrderItem(OrderItem.builder().productId(productId).quantity(1).build());
        var payment=Payment.builder().checkoutGroupId(checkoutId).payerId(buyerId)
                .amount(sampleOrder.getTotalAmount()).method(PaymentMethod.COD).build();
        var product=new Product(UUID.randomUUID(),null,UUID.randomUUID(),"Book","Book",BigDecimal.TEN,
                ProductCondition.USED,PricingStrategy.SELLER_SET,List.of(),Collections.emptyMap());
        product.reserveForCheckout(checkoutId,null,java.time.Instant.now());
        when(orderRepository.findCheckoutGroupIdByOrderId(orderId)).thenReturn(Optional.of(checkoutId));
        when(checkoutRepository.findByIdForUpdate(checkoutId)).thenReturn(Optional.of(checkout));
        when(paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)).thenReturn(Optional.of(payment));
        lenient().when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
    }
}
