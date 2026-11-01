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

    // Lấy giỏ hàng ACTIVE của user, không tồn tại, tạo giỏ hàng mới
    @Transactional
    public Cart getOrCreateActiveCart(UUID userId) {
        return cartRepository.findActiveCartByUserId(userId)
                .orElseGet(() -> createNewCart(userId));
    }

  
    @Transactional
    private Cart createNewCart(UUID userId) {
        Cart newCart = Cart.builder()
                .user(null) 
                .status(CartStatus.ACTIVE)
                .items(new ArrayList<>())
                .subtotal(BigDecimal.ZERO)
                .totalItems(0)
                .build();
        
        // Note: Cần load User entity nếu không nullable
        // Ở đây tạm thời để null, controller sẽ xử lý
        return cartRepository.save(newCart);
    }

    /*
     Bước 1: Đã có trong giỏ thì cập nhật số lượng
     Bước 2: Chưa có thì thêm CartItem mới
     Bước 3. Cập nhật tổng tiền của giỏ 
    */
    @Transactional
    public CartResponse addToCart(UUID userId, AddToCartRequest request) {
        Cart cart = getOrCreateActiveCart(userId);

        CartItem existingItem = cartItemRepository
                .findByCartIdAndListingId(cart.getId(), request.listingId())
                .orElse(null);

        if (existingItem != null) {
            existingItem.updateQuantity(
                    existingItem.getQuantity() + request.quantity());
            if (request.note() != null) {
                existingItem.setNote(request.note());
            }
            cartItemRepository.save(existingItem);
        } else {
            // Thêm CartItem mới
            // Cần gọi service khác để lấy price của listing
            // Ở đây tạm giả định priceAtAddTime lấy controller
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .listingId(request.listingId())
                    .sellerId(null) // Controller sẽ set từ listing 
                    .quantity(request.quantity())
                    .priceAtAddTime(BigDecimal.ZERO) // Controller sẽ set
                    .totalPrice(BigDecimal.ZERO)
                    .note(request.note())
                    .build();   
            cart.addItem(newItem);
        }
        cart.updateCartTotals();
        Cart savedCart = cartRepository.save(cart);

        return cartMapper.toCartResponse(savedCart);
    }

  
    @Transactional
    public CartResponse updateCartItem(UUID userId, UUID listingId, UpdateCartItemRequest request) {
        Cart cart = cartRepository.findActiveCartByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        CartItem item = cartItemRepository
                .findByCartIdAndListingId(cart.getId(), listingId)
                .orElseThrow(() -> new RuntimeException("Item not found in cart"));

        item.updateQuantity(request.quantity());
        
        // Cập nhật note nếu có
        if (request.note() != null) {
            item.setNote(request.note());
        }

        cartItemRepository.save(item);

        cart.updateCartTotals();
        Cart savedCart = cartRepository.save(cart);

        return cartMapper.toCartResponse(savedCart);
    }


    @Transactional
    public CartResponse removeFromCart(UUID userId, UUID listingId) {
        Cart cart = cartRepository.findActiveCartByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        cartItemRepository.deleteByCartIdAndListingId(cart.getId(), listingId);

        cart.updateCartTotals();
        Cart savedCart = cartRepository.save(cart);

        return cartMapper.toCartResponse(savedCart);
    }


    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId) {
        Cart cart = cartRepository.findActiveCartByUserIdWithItems(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        return cartMapper.toCartResponse(cart);
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
        Cart cart = cartRepository.findActiveCartByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        cartItemRepository.deleteByCartId(cart.getId());
        cart.clearCart();
        cartRepository.save(cart);
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
