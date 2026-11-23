package com.moldy.moldymarket.cart.service;

import com.moldy.moldymarket.cart.dto.request.AddToCartRequest;
import com.moldy.moldymarket.cart.dto.request.UpdateCartItemRequest;
import com.moldy.moldymarket.cart.dto.response.CartItemsBySellerResponse;
import com.moldy.moldymarket.cart.dto.response.CartResponse;
import com.moldy.moldymarket.cart.dto.response.CartSummaryResponse;
import com.moldy.moldymarket.cart.entity.Cart;
import com.moldy.moldymarket.cart.entity.CartItem;
import com.moldy.moldymarket.cart.entity.CartStatus;
import com.moldy.moldymarket.cart.mapper.CartMapper;
import com.moldy.moldymarket.cart.repository.CartItemRepository;
import com.moldy.moldymarket.cart.repository.CartRepository;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductStatus;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import com.moldy.moldymarket.store.entity.Store;
import com.moldy.moldymarket.store.entity.StoreStaff;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.store.repository.StoreStaffRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CartMapper cartMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreAccessGuard storeAccessGuard;

    @InjectMocks
    private CartService cartService;

    private UUID userId;
    private UUID cartId;
    private UUID listingId;
    private Cart sampleCart;

    @Test
    void removesOnlyPurchasedProductsAndKeepsOtherCartItems() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        when(cartRepository.findActiveCartByUserIdWithItems(userId)).thenReturn(Optional.of(sampleCart));
        UUID otherProductId = UUID.randomUUID();
        CartItem purchased = purchasedCleanupItem(listingId, 100);
        CartItem remaining = purchasedCleanupItem(otherProductId, 250);
        sampleCart.addItem(purchased);
        sampleCart.addItem(remaining);

        cartService.removePurchasedItems(userId, List.of(listingId, listingId, UUID.randomUUID()));

        assertThat(sampleCart.getItems()).containsExactly(remaining);
        assertThat(sampleCart.getSubtotal()).isEqualByComparingTo("250");
        assertThat(sampleCart.getTotalItems()).isEqualTo(1);
        assertThat(purchased.getCart()).isNull();
        verify(cartRepository).save(sampleCart);
        verifyNoInteractions(cartMapper, cartItemRepository, productRepository);
    }

    @Test
    void purchasedItemCleanupDoesNotCreateAMissingCart() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));

        cartService.removePurchasedItems(userId, List.of(listingId));

        verify(cartRepository, never()).save(any());
        verifyNoInteractions(cartMapper, cartItemRepository);
    }

    @Test
    void retryingPurchasedItemCleanupDoesNotSaveAgain() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        when(cartRepository.findActiveCartByUserIdWithItems(userId)).thenReturn(Optional.of(sampleCart));
        sampleCart.addItem(purchasedCleanupItem(listingId, 100));

        cartService.removePurchasedItems(userId, List.of(listingId));
        cartService.removePurchasedItems(userId, List.of(listingId));

        assertThat(sampleCart.getItems()).isEmpty();
        assertThat(sampleCart.getSubtotal()).isEqualByComparingTo("0");
        assertThat(sampleCart.getTotalItems()).isZero();
        verify(cartRepository, times(1)).save(sampleCart);
    }

    @Test
    void purchasedItemCleanupSkipsAnEmptyProductSelection() {
        cartService.removePurchasedItems(userId, List.of());
        verifyNoInteractions(userRepository, cartRepository, cartMapper, cartItemRepository);
    }

    @Test
    void purchasedItemCleanupRejectsAnUnknownBuyer() {
        assertThatThrownBy(() -> cartService.removePurchasedItems(userId, List.of(listingId)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(cartRepository, cartMapper, cartItemRepository);
    }

    private CartItem purchasedCleanupItem(UUID productId, int amount) {
        return CartItem.builder().listingId(productId).sellerId(UUID.randomUUID())
                .quantity(1).priceAtAddTime(BigDecimal.valueOf(amount))
                .totalPrice(BigDecimal.valueOf(amount)).build();
    }

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        cartId = UUID.randomUUID();
        listingId = UUID.randomUUID();

        sampleCart = Cart.builder()
                .status(CartStatus.ACTIVE)
                .items(new ArrayList<>())
                .subtotal(BigDecimal.ZERO)
                .totalItems(0)
                .build();
        ReflectionTestUtils.setField(sampleCart, "id", cartId);
    }

    @Test
    @DisplayName("should return active cart when cart already exists")
    void should_returnActiveCart_when_cartExists() {
        // Arrange
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        when(cartRepository.findActiveCartByUserIdWithItems(userId)).thenReturn(Optional.of(sampleCart));

        // Act
        Cart cart = cartService.getOrCreateActiveCart(userId);

        // Assert
        assertThat(cart).isNotNull();
        assertThat(cart.getId()).isEqualTo(cartId);
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("should add new item to cart and recalculate totals")
    void should_addToCart_when_itemIsNew() {
        // Arrange
        AddToCartRequest request = new AddToCartRequest(listingId, 1, "Deliver carefully");

        Product product = mock(Product.class);
        UUID sellerId = UUID.randomUUID();
        when(productRepository.findById(listingId)).thenReturn(Optional.of(product));
        when(product.getId()).thenReturn(listingId);
        when(product.getStatus()).thenReturn(ProductStatus.ACTIVE);
        when(product.getSellerId()).thenReturn(sellerId);
        UUID storeId = UUID.randomUUID();
        when(product.getStoreId()).thenReturn(storeId);
        when(product.getPrice()).thenReturn(BigDecimal.valueOf(100000));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        when(cartRepository.findActiveCartByUserIdWithItems(userId)).thenReturn(Optional.of(sampleCart));
        when(cartRepository.save(sampleCart)).thenReturn(sampleCart);

        CartResponse expectedResponse = mock(CartResponse.class);
        when(cartMapper.toCartResponse(sampleCart)).thenReturn(expectedResponse);

        // Act
        CartResponse response = cartService.addToCart(userId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(sampleCart.getItems()).hasSize(1);
        assertThat(sampleCart.getItems().get(0).getListingId()).isEqualTo(listingId);
        assertThat(sampleCart.getItems().get(0).getSellerId()).isEqualTo(sellerId);
        assertThat(sampleCart.getSubtotal()).isEqualByComparingTo("100000");
        assertThat(sampleCart.getTotalItems()).isEqualTo(1);
        verify(storeAccessGuard).isStoreMember(storeId, userId);
        verify(cartRepository).save(sampleCart);
    }

    @Test
    @DisplayName("should keep quantity one when item already in cart")
    void should_updateQuantity_when_itemAlreadyInCart() {
        // Arrange
        AddToCartRequest request = new AddToCartRequest(listingId, 1, "Updated note");

        CartItem existingItem = CartItem.builder()
                .cart(sampleCart)
                .listingId(listingId)
                .quantity(2)
                .priceAtAddTime(BigDecimal.valueOf(100000))
                .totalPrice(BigDecimal.valueOf(200000))
                .build();

        sampleCart.getItems().add(existingItem);
        Product product = mock(Product.class);
        when(productRepository.findById(listingId)).thenReturn(Optional.of(product));
        when(product.getId()).thenReturn(listingId);
        when(product.getStatus()).thenReturn(ProductStatus.ACTIVE);
        when(product.getSellerId()).thenReturn(UUID.randomUUID());
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        when(cartRepository.findActiveCartByUserIdWithItems(userId)).thenReturn(Optional.of(sampleCart));
        when(cartRepository.save(sampleCart)).thenReturn(sampleCart);
        when(cartMapper.toCartResponse(sampleCart)).thenReturn(mock(CartResponse.class));

        // Act
        cartService.addToCart(userId, request);

        // Assert
        assertThat(existingItem.getQuantity()).isEqualTo(1);
        assertThat(sampleCart.getItems()).hasSize(1);
        assertThat(sampleCart.getSubtotal()).isEqualByComparingTo("100000");
        assertThat(existingItem.getPriceAtAddTime()).isEqualByComparingTo("100000");
        assertThat(existingItem.getNote()).isEqualTo("Updated note");
        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void createsFirstCartWithTheAuthenticatedUserUnderTheUserLock() {
        User user = new User();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(cartRepository.save(any(Cart.class))).thenAnswer(call -> call.getArgument(0));

        Cart cart = cartService.getOrCreateActiveCart(userId);

        assertThat(cart.getUser()).isSameAs(user);
        assertThat(cart.getItems()).isEmpty();
        var order = inOrder(userRepository, cartRepository);
        order.verify(userRepository).findByIdForUpdate(userId);
        order.verify(cartRepository).findActiveCartByUserIdWithItems(userId);
        order.verify(cartRepository).save(cart);
    }

    @Test
    void returnsEmptyCartWithoutCreatingDatabaseRows() {
        CartResponse response = cartService.getCart(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.id()).isNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalItems()).isZero();
        verify(cartRepository, never()).save(any());
    }

    @Test
    void rejectsMissingProductBeforeCreatingCart() {
        assertThatThrownBy(() -> cartService.addToCart(userId, new AddToCartRequest(listingId, null, null)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
        verifyNoInteractions(userRepository, cartRepository);
    }

    @Test
    void rejectsInactiveProductBeforeCreatingCart() {
        Product product = mock(Product.class);
        when(productRepository.findById(listingId)).thenReturn(Optional.of(product));
        when(product.getStatus()).thenReturn(ProductStatus.HIDDEN);
        assertThatThrownBy(() -> cartService.addToCart(userId, new AddToCartRequest(listingId, null, null)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_ACTIVE);
        verifyNoInteractions(userRepository, cartRepository);
    }

    @Test
    void defaultsQuantityToOneAndRejectsLargerValuesAtTheRequestBoundary() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(new AddToCartRequest(listingId, null, null).quantity()).isEqualTo(1);
            assertThat(validator.validate(new AddToCartRequest(listingId, null, null))).isEmpty();
            assertThat(validator.validate(new AddToCartRequest(listingId, 2, null))).isNotEmpty();
        }
    }

    @Test
    @DisplayName("should update cart item quantity and note")
    void should_updateCartItem_when_itemExists() {
        // Arrange
        UpdateCartItemRequest request = new UpdateCartItemRequest(1, "New note");
        CartItem item = CartItem.builder()
                .cart(sampleCart)
                .listingId(listingId)
                .quantity(1)
                .priceAtAddTime(BigDecimal.valueOf(50000))
                .totalPrice(BigDecimal.valueOf(50000))
                .build();

        sampleCart.getItems().add(item);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        when(cartRepository.findActiveCartByUserIdWithItems(userId)).thenReturn(Optional.of(sampleCart));
        when(cartRepository.save(sampleCart)).thenReturn(sampleCart);
        when(cartMapper.toCartResponse(sampleCart)).thenReturn(mock(CartResponse.class));

        // Act
        CartResponse response = cartService.updateCartItem(userId, listingId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(item.getQuantity()).isEqualTo(1);
        assertThat(sampleCart.getSubtotal()).isEqualByComparingTo("50000");
        assertThat(item.getNote()).isEqualTo("New note");
    }

    @Test
    @DisplayName("should remove item from cart and recalculate totals")
    void should_removeFromCart_when_invoked() {
        // Arrange
        CartItem removed = CartItem.builder().cart(sampleCart).listingId(listingId)
                .quantity(1).priceAtAddTime(BigDecimal.valueOf(50000)).totalPrice(BigDecimal.valueOf(50000)).build();
        CartItem retained = CartItem.builder().cart(sampleCart).listingId(UUID.randomUUID())
                .quantity(1).priceAtAddTime(BigDecimal.valueOf(30000)).totalPrice(BigDecimal.valueOf(30000)).build();
        sampleCart.addItem(removed);
        sampleCart.addItem(retained);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        when(cartRepository.findActiveCartByUserIdWithItems(userId)).thenReturn(Optional.of(sampleCart));
        when(cartRepository.save(sampleCart)).thenReturn(sampleCart);
        when(cartMapper.toCartResponse(sampleCart)).thenReturn(mock(CartResponse.class));

        // Act
        CartResponse response = cartService.removeFromCart(userId, listingId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(sampleCart.getItems()).containsExactly(retained);
        assertThat(sampleCart.getSubtotal()).isEqualByComparingTo("30000");
        assertThat(sampleCart.getTotalItems()).isEqualTo(1);
        assertThat(removed.getCart()).isNull();
        verifyNoInteractions(cartItemRepository);
        verify(cartRepository).save(sampleCart);
    }

    @Test
    @DisplayName("should get cart summary when cart exists")
    void should_getCartSummary_when_cartExists() {
        // Arrange
        when(cartRepository.findActiveCartByUserId(userId)).thenReturn(Optional.of(sampleCart));
        CartSummaryResponse summaryResponse = mock(CartSummaryResponse.class);
        when(cartMapper.toCartSummaryResponse(sampleCart)).thenReturn(summaryResponse);

        // Act
        CartSummaryResponse response = cartService.getCartSummary(userId);

        // Assert
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("should group cart items by seller")
    void should_groupCartItemsBySeller_when_itemsBelongToMultipleSellers() {
        // Arrange
        UUID sellerA = UUID.randomUUID();
        when(cartRepository.findActiveCartByUserIdWithItems(userId)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.findDistinctSellerIdsByCartId(cartId)).thenReturn(List.of(sellerA));

        CartItem itemA = CartItem.builder()
                .cart(sampleCart)
                .sellerId(sellerA)
                .quantity(2)
                .totalPrice(BigDecimal.valueOf(200000))
                .build();
        when(cartItemRepository.findByCartIdAndSellerId(cartId, sellerA)).thenReturn(List.of(itemA));
        when(cartMapper.toCartItemResponseList(List.of(itemA))).thenReturn(Collections.emptyList());

        // Act
        List<CartItemsBySellerResponse> result = cartService.getCartBySellerGrouping(userId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).sellerId()).isEqualTo(sellerA);
        assertThat(result.get(0).subtotalBySeller()).isEqualByComparingTo(BigDecimal.valueOf(200000));
        assertThat(result.get(0).totalItemsBySeller()).isEqualTo(2);
    }

    @Test
    @DisplayName("should clear cart and delete all cart items")
    void should_clearCart_when_invoked() {
        // Arrange
        sampleCart.addItem(CartItem.builder().cart(sampleCart).listingId(listingId)
                .quantity(1).priceAtAddTime(BigDecimal.TEN).totalPrice(BigDecimal.TEN).build());
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        when(cartRepository.findActiveCartByUserIdWithItems(userId)).thenReturn(Optional.of(sampleCart));

        // Act
        cartService.clearCart(userId);

        // Assert
        assertThat(sampleCart.getItems()).isEmpty();
        assertThat(sampleCart.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(sampleCart.getTotalItems()).isZero();
        verifyNoInteractions(cartItemRepository);
        verify(cartRepository).save(sampleCart);
    }

    @Test
    void removingAnItemWithoutACartReturnsEmptyCart() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        CartResponse response = cartService.removeFromCart(userId, listingId);
        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isZero();
        verify(cartRepository, never()).save(any());
    }

    @Test
    void retryingRemovalOfAnAbsentItemKeepsTheCartUnchanged() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        when(cartRepository.findActiveCartByUserIdWithItems(userId)).thenReturn(Optional.of(sampleCart));
        when(cartMapper.toCartResponse(sampleCart)).thenReturn(mock(CartResponse.class));
        cartService.removeFromCart(userId, listingId);
        verify(cartRepository, never()).save(any());
        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void clearingAnAbsentCartDoesNotCreateOne() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        cartService.clearCart(userId);
        verify(cartRepository, never()).save(any());
        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void updatingAnAbsentItemReturnsDataConflict() {
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(new User()));
        when(cartRepository.findActiveCartByUserIdWithItems(userId)).thenReturn(Optional.of(sampleCart));
        assertThatThrownBy(() -> cartService.updateCartItem(userId, listingId, new UpdateCartItemRequest(1, "note")))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DATA_CONFLICT);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateRequestRejectsQuantityAboveOne() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new UpdateCartItemRequest(2, "note"))).isNotEmpty();
        }
    }

    @Test
    void rejectsTheBuyersOwnPersonalListingBeforeCreatingACart() {
        Product product = mock(Product.class);
        when(productRepository.findById(listingId)).thenReturn(Optional.of(product));
        when(product.getStatus()).thenReturn(ProductStatus.ACTIVE);
        when(product.getSellerId()).thenReturn(userId);

        assertThatThrownBy(() -> cartService.addToCart(userId, new AddToCartRequest(listingId, 1, null)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_OWN_PRODUCT);
        verifyNoInteractions(userRepository, cartRepository, storeAccessGuard);
    }

    @Test
    void rejectsStoreOwnersEvenWhenAnotherAccountCreatedTheProduct() {
        UUID storeId = UUID.randomUUID();
        Product product = mock(Product.class);
        when(productRepository.findById(listingId)).thenReturn(Optional.of(product));
        when(product.getStatus()).thenReturn(ProductStatus.ACTIVE);
        when(product.getSellerId()).thenReturn(UUID.randomUUID());
        when(product.getStoreId()).thenReturn(storeId);
        when(storeAccessGuard.isStoreMember(storeId, userId)).thenReturn(true);

        assertThatThrownBy(() -> cartService.addToCart(userId, new AddToCartRequest(listingId, 1, null)))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_OWN_PRODUCT);
        verifyNoInteractions(userRepository, cartRepository);
    }

    @Test
    void recognizesBothOwnerAndStaffMembershipWithoutRequiringDelegatedPermissions() {
        StoreRepository stores = mock(StoreRepository.class);
        StoreStaffRepository staff = mock(StoreStaffRepository.class);
        Store store = mock(Store.class);
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        StoreAccessGuard guard = new StoreAccessGuard(stores, staff);
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(store.getOwnerId()).thenReturn(ownerId);
        assertThat(guard.isStoreMember(storeId, ownerId)).isTrue();

        StoreStaff member = mock(StoreStaff.class);
        when(staff.findByUserId(userId)).thenReturn(Optional.of(member));
        when(member.getStore()).thenReturn(store);
        when(store.getId()).thenReturn(storeId);
        assertThat(guard.isStoreMember(storeId, userId)).isTrue();
    }
}
