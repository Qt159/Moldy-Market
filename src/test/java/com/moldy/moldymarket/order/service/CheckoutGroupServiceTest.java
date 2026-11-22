package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.request.CheckoutGroupRequest;
import com.moldy.moldymarket.order.dto.request.OrderItemRequest;
import com.moldy.moldymarket.order.dto.response.CheckoutResponse;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.mapper.CheckoutMapper;
import com.moldy.moldymarket.order.mapper.OrderMapper;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.payment.dto.response.PaymentResponse;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.service.PaymentService;
import com.moldy.moldymarket.product.entity.PricingStrategy;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductCondition;
import com.moldy.moldymarket.product.entity.ProductStatus;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutGroupServiceTest {

    @Mock
    private CheckoutGroupRepository checkoutGroupRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private CheckoutMapper checkoutMapper;

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private StoreAccessGuard storeAccessGuard;

    @InjectMocks
    private CheckoutGroupService checkoutGroupService;

    private User buyer;
    private UUID buyerId;
    private UUID sellerId;
    private UUID productId;
    private Product product;

    @BeforeEach
    void setUp() {
        buyerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        productId = UUID.randomUUID();

        buyer = new User();
        buyer.setId(buyerId);

        product = new Product(
                sellerId, null, UUID.randomUUID(),
                "Vintage Camera", "Classic 35mm",
                BigDecimal.valueOf(2500000), ProductCondition.LIKE_NEW,
                PricingStrategy.SELLER_SET, List.of("https://example.com/cam.jpg"), Map.of()
        );
        ReflectionTestUtils.setField(product, "id", productId);
    }

    @Test
    @DisplayName("should create checkout group successfully and return CheckoutResponse")
    void should_createCheckoutGroup_when_valid() {
        // Arrange
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        when(checkoutGroupRepository.save(any())).thenAnswer(invocation -> {
            CheckoutGroup checkout = invocation.getArgument(0);
            ReflectionTestUtils.setField(checkout, "id", UUID.randomUUID());
            return checkout;
        });

        OrderItemRequest itemRequest = new OrderItemRequest(productId, 1);
        CheckoutGroupRequest request = new CheckoutGroupRequest(
                List.of(itemRequest), "Nguyen Van A", "0901234567",
                "123 Le Loi, Q1, HCMC", PaymentMethod.ONLINE_BANKING, "Fragile"
        );

        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(paymentService.createPayment(any(), eq(PaymentMethod.ONLINE_BANKING)))
                .thenReturn(mock(PaymentResponse.class));

        // Act
        CheckoutResponse response = checkoutGroupService.createCheckoutGroup(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2500000));
        verify(checkoutGroupRepository).save(any(CheckoutGroup.class));
        verify(orderRepository).saveAll(anyList());
        verify(paymentService).createPayment(any(), eq(PaymentMethod.ONLINE_BANKING));
    }

    @Test
    @DisplayName("should throw INVALID_ORDER_ITEMS when item quantity is null or <= 0")
    void should_throwInvalidOrderItems_when_quantityZeroOrNegative() {
        // Arrange
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        OrderItemRequest itemRequest = new OrderItemRequest(productId, 0);
        CheckoutGroupRequest request = new CheckoutGroupRequest(
                List.of(itemRequest), "Buyer", "0901234567", "Address", PaymentMethod.COD, null
        );

        // Act & Assert
        assertThatThrownBy(() -> checkoutGroupService.createCheckoutGroup(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_ITEMS);

        verify(checkoutGroupRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw DUPLICATE_PRODUCT when request contains duplicate product id")
    void should_throwDuplicateProduct_when_productIdRepeated() {
        // Arrange
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        OrderItemRequest item1 = new OrderItemRequest(productId, 1);
        OrderItemRequest item2 = new OrderItemRequest(productId, 1);
        CheckoutGroupRequest request = new CheckoutGroupRequest(
                List.of(item1, item2), "Buyer", "0901234567", "Address", PaymentMethod.COD, null
        );

        // Act & Assert
        assertThatThrownBy(() -> checkoutGroupService.createCheckoutGroup(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_PRODUCT);
    }

    @Test
    @DisplayName("should throw PRODUCT_NOT_FOUND when database returns fewer products than requested")
    void should_throwProductNotFound_when_productMissingInDb() {
        // Arrange
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        OrderItemRequest item = new OrderItemRequest(productId, 1);
        CheckoutGroupRequest request = new CheckoutGroupRequest(
                List.of(item), "Buyer", "0901234567", "Address", PaymentMethod.COD, null
        );

        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> checkoutGroupService.createCheckoutGroup(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("should throw PRODUCT_NOT_ACTIVE when product is HIDDEN, SOLD or DELETED")
    void should_throwProductNotActive_when_productStatusNotActive() {
        // Arrange
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        product.changeStatus(ProductStatus.HIDDEN);

        OrderItemRequest item = new OrderItemRequest(productId, 1);
        CheckoutGroupRequest request = new CheckoutGroupRequest(
                List.of(item), "Buyer", "0901234567", "Address", PaymentMethod.COD, null
        );

        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        // Act & Assert
        assertThatThrownBy(() -> checkoutGroupService.createCheckoutGroup(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("should throw NOT_ALLOWED_ACCESS_CHECKOUT_GROUP when user is not checkout buyer")
    void should_throwAccessDenied_when_gettingCheckoutBelongingToAnother() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        when(currentUser.findCurrentUser()).thenReturn(buyer);

        UUID checkoutId = UUID.randomUUID();
        CheckoutGroup checkoutGroup = CheckoutGroup.builder()
                .buyerId(otherUserId)
                .build();
        ReflectionTestUtils.setField(checkoutGroup, "id", checkoutId);

        when(checkoutGroupRepository.findById(checkoutId)).thenReturn(Optional.of(checkoutGroup));

        // Act & Assert
        assertThatThrownBy(() -> checkoutGroupService.getCheckoutById(checkoutId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ALLOWED_ACCESS_CHECKOUT_GROUP);
    }

    @Test
    void rejectsQuantityGreaterThanOneBeforeLoadingProducts() {
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        CheckoutGroupRequest request = checkoutRequest(2);

        assertThatThrownBy(() -> checkoutGroupService.createCheckoutGroup(request))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ORDER_ITEMS);
        verifyNoInteractions(productRepository, checkoutGroupRepository, orderRepository, paymentService);
    }

    @Test
    void validatesExactlyOneItemAtTheRequestBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new OrderItemRequest(productId, 1))).isEmpty();
            assertThat(new OrderItemRequest(productId, null).quantity()).isEqualTo(1);
            for (Integer quantity : Arrays.asList(0, 2, 100)) {
                assertThat(validator.validate(new OrderItemRequest(productId, quantity)))
                        .anyMatch(violation -> violation.getPropertyPath().toString().equals("quantity"));
            }
        }
    }

    @Test
    void rejectsBuyingOwnProductBeforeCreatingCheckout() {
        ReflectionTestUtils.setField(product, "sellerId", buyerId);
        assertOwnProductRejected();
    }

    @Test
    void rejectsBuyingFromAnyStoreWhereBuyerIsAMember() {
        UUID storeId = UUID.randomUUID();
        ReflectionTestUtils.setField(product, "storeId", storeId);
        when(storeAccessGuard.isStoreMember(storeId, buyerId)).thenReturn(true);
        assertOwnProductRejected();
        verify(storeAccessGuard).isStoreMember(storeId, buyerId);
    }

    private void assertOwnProductRejected() {
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> checkoutGroupService.createCheckoutGroup(checkoutRequest(1)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode().name()).isEqualTo("CHECKOUT_OWN_PRODUCT"));
        verifyNoInteractions(checkoutGroupRepository, orderRepository, paymentService);
    }

    private CheckoutGroupRequest checkoutRequest(int quantity) {
        return new CheckoutGroupRequest(List.of(new OrderItemRequest(productId, quantity)),
                "Buyer", "0901234567", "Address", PaymentMethod.COD, null);
    }
}
