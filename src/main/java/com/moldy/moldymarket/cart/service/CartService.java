package com.moldy.moldymarket.cart.service;

import com.moldy.moldymarket.cart.dto.request.AddToCartRequest;
import com.moldy.moldymarket.cart.dto.request.UpdateCartItemRequest;
import com.moldy.moldymarket.cart.dto.response.*;
import com.moldy.moldymarket.cart.entity.Cart;
import com.moldy.moldymarket.cart.entity.CartItem;
import com.moldy.moldymarket.cart.entity.CartStatus;
import com.moldy.moldymarket.cart.mapper.CartMapper;
import com.moldy.moldymarket.cart.repository.CartItemRepository;
import com.moldy.moldymarket.cart.repository.CartRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductStatus;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import com.moldy.moldymarket.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartService {
    
    CartRepository cartRepository;
    CartItemRepository cartItemRepository;
    CartMapper cartMapper;
    ProductRepository productRepository;
    UserRepository userRepository;
    StoreAccessGuard storeAccessGuard;

    // Lấy giỏ hàng ACTIVE của user, không tồn tại, tạo giỏ hàng mới
    @Transactional
    public Cart getOrCreateActiveCart(UUID userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return cartRepository.findActiveCartByUserIdWithItems(userId)
                .orElseGet(() -> createNewCart(userId));
    }

  
    private Cart createNewCart(UUID userId) {
        Cart newCart = Cart.builder()
                .user(userRepository.getReferenceById(userId))
                .status(CartStatus.ACTIVE)
                .items(new ArrayList<>())
                .subtotal(BigDecimal.ZERO)
                .totalItems(0)
                .build();
        
        return cartRepository.save(newCart);
    }

    /*
     Bước 1: Đã có trong giỏ thì cập nhật số lượng
     Bước 2: Chưa có thì thêm CartItem mới
     Bước 3. Cập nhật tổng tiền của giỏ 
    */
    @Transactional
    public CartResponse addToCart(UUID userId, AddToCartRequest request) {
        Product product = productRepository.findById(request.listingId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new AppException(ErrorCode.PRODUCT_NOT_ACTIVE);
        }

        boolean isSeller = product.getSellerId().equals(userId);
        boolean isStoreMember = product.getStoreId() != null
                && storeAccessGuard.isStoreMember(product.getStoreId(), userId);

        if (isSeller || isStoreMember) {
            throw new AppException(ErrorCode.CART_OWN_PRODUCT);
        }

        Cart cart = getOrCreateActiveCart(userId);
        CartItem item = cart.findItemByListingId(product.getId());

        if (item == null) {
            item = CartItem.builder()
                    .cart(cart)
                    .listingId(product.getId())
                    .sellerId(product.getSellerId())
                    .quantity(1)
                    .priceAtAddTime(product.getPrice())
                    .totalPrice(product.getPrice())
                    .note(request.note())
                    .build();

            cart.addItem(item);
        } else {
            item.updateQuantity(1);

            if (request.note() != null) {
                item.setNote(request.note());
            }
        }

        cart.updateCartTotals();
        Cart savedCart = cartRepository.save(cart);

        return cartMapper.toCartResponse(savedCart);
    }

  
    @Transactional
    public CartResponse updateCartItem(UUID userId, UUID listingId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateActiveCart(userId);
        CartItem item = cart.findItemByListingId(listingId);

        if (item == null) {
            throw new AppException(ErrorCode.DATA_CONFLICT);
        }

        item.updateQuantity(1);

        if (request.note() != null) {
            item.setNote(request.note());
        }

        cart.updateCartTotals();
        Cart savedCart = cartRepository.save(cart);

        return cartMapper.toCartResponse(savedCart);
    }


    @Transactional
    public CartResponse removeFromCart(UUID userId, UUID listingId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Cart cart = cartRepository.findActiveCartByUserIdWithItems(userId)
                .orElse(null);

        if (cart == null) {
            return getCart(userId);
        }

        CartItem item = cart.findItemByListingId(listingId);

        if (item != null) {
            cart.removeItem(item);
            cartRepository.save(cart);
        }

        return cartMapper.toCartResponse(cart);
    }

    @Transactional
    public void removePurchasedItems(
            UUID userId,
            Collection<UUID> productIds
    ) {
        Objects.requireNonNull(userId, "userId");

        Set<UUID> purchasedIds = Set.copyOf(productIds);
        if (purchasedIds.isEmpty()) {
            return;
        }

        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Cart cart = cartRepository.findActiveCartByUserIdWithItems(userId)
                .orElse(null);

        if (cart == null) {
            return;
        }

        List<CartItem> purchasedItems = cart.getItems().stream()
                .filter(item -> purchasedIds.contains(item.getListingId()))
                .toList();

        if (purchasedItems.isEmpty()) {
            return;
        }

        purchasedItems.forEach(cart::removeItem);
        cartRepository.save(cart);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId) {
        return cartRepository.findActiveCartByUserIdWithItems(userId)
                .map(cartMapper::toCartResponse)
                .orElseGet(() -> new CartResponse(
                        null,
                        userId,
                        CartStatus.ACTIVE,
                        List.of(),
                        BigDecimal.ZERO,
                        0,
                        null,
                        null,
                        null
                ));
    }

   
    @Transactional(readOnly = true)
    public CartSummaryResponse getCartSummary(UUID userId) {
        Cart cart = cartRepository.findActiveCartByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        return cartMapper.toCartSummaryResponse(cart);
    }

   
    @Transactional(readOnly = true)
    public List<CartItemsBySellerResponse> getCartBySellerGrouping(UUID userId) {
        Cart cart = cartRepository.findActiveCartByUserIdWithItems(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        List<UUID> sellerIds = cartItemRepository.findDistinctSellerIdsByCartId(cart.getId());

        return sellerIds.stream()
                .map(sellerId -> {
                    List<CartItem> itemsBySeller = cartItemRepository
                            .findByCartIdAndSellerId(cart.getId(), sellerId);

                    BigDecimal subtotalBySeller = itemsBySeller.stream()
                            .map(CartItem::getTotalPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    Integer totalItemsBySeller = itemsBySeller.stream()
                            .mapToInt(CartItem::getQuantity)
                            .sum();

                    return new CartItemsBySellerResponse(
                            sellerId,
                            cartMapper.toCartItemResponseList(itemsBySeller),
                            subtotalBySeller,
                            totalItemsBySeller
                    );
                })
                .collect(Collectors.toList());
    }

  
    @Transactional
    public void clearCart(UUID userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        cartRepository.findActiveCartByUserIdWithItems(userId)
                .ifPresent(cart -> {
                    cart.clearCart();
                    cartRepository.save(cart);
                });
    }

   
    @Transactional
    public void markCartAsAbandoned(UUID cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        if (cart.getStatus() == CartStatus.ACTIVE) {
            cart.setStatus(CartStatus.ABANDONED);
            cart.setAbandonedAt(Instant.now());
            cartRepository.save(cart);
        }
    }


    @Transactional(readOnly = true)
    public List<Cart> findAbandonedCarts(Instant thirtyDaysAgo) {
        return cartRepository.findAbandonedCarts(thirtyDaysAgo);
    }

    // Check user có sản phẩm trong giỏ hàng không
    @Transactional(readOnly = true)
    public boolean isListingInCart(UUID userId, UUID listingId) {
        Cart cart = cartRepository.findActiveCartByUserId(userId)
                .orElse(null);

        if (cart == null) {
            return false;
        }

        return cartItemRepository.existsByCartIdAndListingId(cart.getId(), listingId);
    }

    @Transactional(readOnly = true)
    public Integer getCartItemCount(UUID userId) {
        Integer count = cartRepository.countItemsInActiveCart(userId);
        return count != null ? count : 0;
    }
}
