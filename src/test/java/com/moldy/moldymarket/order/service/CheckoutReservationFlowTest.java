package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.request.CheckoutGroupRequest;
import com.moldy.moldymarket.order.dto.request.OrderItemRequest;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.mapper.CheckoutMapper;
import com.moldy.moldymarket.order.mapper.OrderMapper;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.service.PaymentService;
import com.moldy.moldymarket.product.entity.*;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import com.moldy.moldymarket.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
class CheckoutReservationFlowTest {
    @Mock CheckoutGroupRepository checkoutGroupRepository;
    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock CurrentUser currentUser;
    @Mock CheckoutMapper checkoutMapper;
    @Mock PaymentService paymentService;
    @Mock OrderMapper orderMapper;
    @Mock StoreAccessGuard storeAccessGuard;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @InjectMocks CheckoutGroupService service;

    final UUID productId = UUID.randomUUID();
    final UUID checkoutId = UUID.randomUUID();
    Product product;

    @BeforeEach
    void setUp() {
        var buyer = new User();
        buyer.setId(UUID.randomUUID());
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        product = new Product(UUID.randomUUID(), null, UUID.randomUUID(), "Book", "Used book",
                BigDecimal.TEN, ProductCondition.USED, PricingStrategy.SELLER_SET, List.of(), Map.of());
        ReflectionTestUtils.setField(product, "id", productId);
        lenient().when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        lenient().when(checkoutGroupRepository.save(any())).thenAnswer(invocation -> {
            CheckoutGroup checkout = invocation.getArgument(0);
            ReflectionTestUtils.setField(checkout, "id", checkoutId);
            return checkout;
        });
    }

    @Test
    void onlineCheckoutHoldsItsProductForExactlyFifteenMinutes() {
        var before = Instant.now();
        var response = service.createCheckoutGroup(request(PaymentMethod.ONLINE_BANKING));
        var after = Instant.now();

        assertThat(product.getReservedCheckoutId()).isEqualTo(checkoutId);
        assertThat(response.expiredAt()).isBetween(before.plusSeconds(900), after.plusSeconds(900));
        assertThat(product.getReservationExpiresAt()).isEqualTo(response.expiredAt());
        assertThat(product.isReservedAt(response.expiredAt().minusNanos(1))).isTrue();
        assertThat(product.isReservedAt(response.expiredAt())).isFalse();
        var locks = inOrder(productRepository, checkoutGroupRepository);
        locks.verify(productRepository).findByIdForUpdate(productId);
        locks.verify(checkoutGroupRepository).save(any());
    }

    @Test
    void codCheckoutHoldsItsProductWithoutAnAutomaticDeadline() {
        var response = service.createCheckoutGroup(request(PaymentMethod.COD));
        assertThat(product.getReservedCheckoutId()).isEqualTo(checkoutId);
        assertThat(response.expiredAt()).isNull();
        assertThat(product.getReservationExpiresAt()).isNull();
        assertThat(product.isReservedAt(Instant.now().plusSeconds(86400))).isTrue();
    }

    @Test
    void anotherBuyerCannotCreateCheckoutForAnAlreadyHeldProduct() {
        var firstCheckout = UUID.randomUUID();
        product.reserveForCheckout(firstCheckout, null, Instant.now());
        assertThatThrownBy(() -> service.createCheckoutGroup(request(PaymentMethod.COD)))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode().name()).isEqualTo("PRODUCT_RESERVED"));
        assertThat(product.getReservedCheckoutId()).isEqualTo(firstCheckout);
        verifyNoInteractions(checkoutGroupRepository, orderRepository, paymentService);
    }

    @Test
    void locksMultipleProductsInStableIdOrder() {
        UUID secondId = UUID.randomUUID();
        var second = new Product(UUID.randomUUID(), null, UUID.randomUUID(), "Chair", "Used chair",
                BigDecimal.ONE, ProductCondition.USED, PricingStrategy.SELLER_SET, List.of(), Map.of());
        ReflectionTestUtils.setField(second, "id", secondId);
        when(productRepository.findByIdForUpdate(secondId)).thenReturn(Optional.of(second));
        var request = new CheckoutGroupRequest(
                List.of(new OrderItemRequest(productId, 1), new OrderItemRequest(secondId, 1)),
                "Buyer", "0901234567", "Address", PaymentMethod.COD, null);
        service.createCheckoutGroup(request);
        var lockOrder = inOrder(productRepository);
        for (UUID id : List.of(productId, secondId).stream().sorted().toList()) {
            lockOrder.verify(productRepository).findByIdForUpdate(id);
        }
        assertThat(second.getReservedCheckoutId()).isEqualTo(checkoutId);
    }

    private CheckoutGroupRequest request(PaymentMethod method) {
        return new CheckoutGroupRequest(List.of(new OrderItemRequest(productId, 1)),
                "Buyer", "0901234567", "Address", method, null);
    }
}
