package com.moldy.moldymarket.cart.controller;

import com.moldy.moldymarket.cart.dto.request.AddToCartRequest;
import com.moldy.moldymarket.cart.dto.request.UpdateCartItemRequest;
import com.moldy.moldymarket.cart.dto.response.*;
import com.moldy.moldymarket.cart.service.CartService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@Tag(name = "Giỏ hàng (Shopping Cart)",
        description = "Quản lý giỏ hàng của người dùng")
@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartController {

    CartService cartService;


    @Operation(summary = "Lấy giỏ hàng",
            description = "Lấy danh sách sản phẩm trong giỏ hàng của người dùng hiện tại")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CartResponse cart = cartService.getCart(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Cart retrieved successfully", cart));
    }

    //chỉ trả về số lượng & tổng tiền
     
    @Operation(summary = "Lấy tóm tắt giỏ hàng",
            description = "Lấy số lượng sản phẩm và tổng tiền trong giỏ hàng (dùng cho cart icon)")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<CartSummaryResponse>> getCartSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CartSummaryResponse summary = cartService.getCartSummary(userDetails.getUserId());
        return ResponseEntity.ok(
                ApiResponse.success("Cart summary retrieved successfully", summary)
        );
    }

    //danh sách các seller với sản phẩm tương ứng
    @Operation(summary = "Lấy giỏ hàng nhóm theo seller",
            description = "Lấy danh sách sản phẩm trong giỏ hàng được nhóm theo từng gian hàng")
    @GetMapping("/by-seller")
    public ResponseEntity<ApiResponse<List<CartItemsBySellerResponse>>> getCartBySellerGrouping(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<CartItemsBySellerResponse> cartBySeller = cartService.getCartBySellerGrouping(userDetails.getUserId());
        return ResponseEntity.ok(
                ApiResponse.success("Cart grouped by seller retrieved successfully", cartBySeller)
        );
    }

    /* 
    - Đã tồn tại trong giỏ thì cập nhật quantity
    - Chưa tồn tại thì thêm mới
     */
    @Operation(summary = "Thêm sản phẩm vào giỏ hàng",
            description = "Thêm một sản phẩm vào giỏ hàng của người dùng. Nếu sản phẩm đã tồn tại, số lượng sẽ được cộng thêm")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AddToCartRequest request) {
        CartResponse cart = cartService.addToCart(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product added to cart successfully", cart));
    }

    
    @Operation(summary = "Cập nhật sản phẩm trong giỏ hàng",
            description = "Cập nhật số lượng hoặc ghi chú của sản phẩm trong giỏ hàng")
    @PatchMapping("/items/{listingId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateCartItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID listingId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        CartResponse cart = cartService.updateCartItem(userDetails.getUserId(), listingId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Product updated in cart successfully", cart)
        );
    }

    
    @Operation(summary = "Xóa sản phẩm khỏi giỏ hàng",
            description = "Xóa một sản phẩm khỏi giỏ hàng của người dùng")
    @DeleteMapping("/items/{listingId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeFromCart(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID listingId) {
        CartResponse cart = cartService.removeFromCart(userDetails.getUserId(), listingId);
        return ResponseEntity.ok(
                ApiResponse.success("Product removed from cart successfully", cart)
        );
    }

    //Dùng khi user click "Clear cart" hoặc sau khi checkout thành công
    @Operation(summary = "Xóa toàn bộ giỏ hàng",
            description = "Xóa tất cả sản phẩm khỏi giỏ hàng của người dùng")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        cartService.clearCart(userDetails.getUserId());
        return ResponseEntity.ok(
                ApiResponse.success("Cart cleared successfully", null)
        );
    }

    
    @Operation(summary = "Kiểm tra sản phẩm có trong giỏ hàng",
            description = "Kiểm tra xem sản phẩm có tồn tại trong giỏ hàng của người dùng không")
    @GetMapping("/items/{listingId}/exists")
    public ResponseEntity<ApiResponse<Boolean>> isListingInCart(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID listingId) {
        boolean exists = cartService.isListingInCart(userDetails.getUserId(), listingId);
        return ResponseEntity.ok(
                ApiResponse.success("Check completed", exists)
        );
    }

    
    @Operation(summary = "Đếm số sản phẩm trong giỏ hàng",
            description = "Lấy tổng số lượng sản phẩm trong giỏ hàng của người dùng")
    @GetMapping("/items/count")
    public ResponseEntity<ApiResponse<Integer>> getCartItemCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Integer count = cartService.getCartItemCount(userDetails.getUserId());
        return ResponseEntity.ok(
                ApiResponse.success("Item count retrieved successfully", count)
        );
    }
}
