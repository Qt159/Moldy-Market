
package com.moldy.moldymarket.wishlist.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.wishlist.dto.WishlistItemResponse;
import com.moldy.moldymarket.wishlist.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Wishlist",
     description = "Quản lý danh sách yêu thích sản phẩm")
@RestController
@RequestMapping("/api/v1/wishlists")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Validated
public class WishlistController {

    WishlistService wishlistService;

    @Operation(summary = "Thêm sản phẩm vào danh sách yêu thích",
               description = "Thêm một sản phẩm vào danh sách yêu thích của người dùng")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PROFILE_MANAGE + "')")
    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<WishlistItemResponse>> addToWishlist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID productId) {
        WishlistItemResponse response = wishlistService.addToWishlist(
                userDetails.getUserId(), productId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product added to wishlist successfully", response));
    }

    @Operation(summary = "Xóa sản phẩm khỏi danh sách yêu thích",
               description = "Xóa sản phẩm khỏi wishlist của người dùng hiện tại")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PROFILE_MANAGE + "')")
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeFromWishlist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID productId) {
        wishlistService.removeFromWishlist(userDetails.getUserId(), productId);

        return ResponseEntity.ok(
                ApiResponse.success("Product removed from wishlist successfully", null));
    }

    @Operation(summary = "Lấy danh sách yêu thích",
               description = "Lấy danh sách sản phẩm yêu thích có hỗ trợ phân trang")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PROFILE_MANAGE + "')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<WishlistItemResponse>>> getWishlist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0")
            @Min(0) int page,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(50) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")));

        Page<WishlistItemResponse> wishlist =
                wishlistService.getWishlist(userDetails.getUserId(), pageable);

        return ResponseEntity.ok(
                ApiResponse.success("Wishlist retrieved successfully", wishlist));
    }

    @Operation(summary = "Kiểm tra sản phẩm có trong danh sách yêu thích",
               description = "Kiểm tra sản phẩm đã được người dùng lưu hay chưa")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PROFILE_MANAGE + "')")
    @GetMapping("/check/{productId}")
    public ResponseEntity<ApiResponse<Boolean>> isProductWishlisted(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID productId) {
        boolean exists = wishlistService.isProductWishlisted(
                userDetails.getUserId(), productId);

        return ResponseEntity.ok(ApiResponse.success("Check completed", exists));
    }

    @Operation(summary = "Đếm số sản phẩm trong danh sách yêu thích",
               description = "Lấy tổng số sản phẩm yêu thích của người dùng hiện tại")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PROFILE_MANAGE + "')")
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> getWishlistCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long count = wishlistService.getWishlistCount(
                userDetails.getUserId());

        return ResponseEntity.ok(
                ApiResponse.success("Wishlist count retrieved successfully", count));
    }

    @Operation(summary = "Lấy số lượng người dùng yêu thích sản phẩm",
               description = "Lấy tổng số người dùng đã thêm sản phẩm vào wishlist")
    @GetMapping("/products/{productId}/count")
    public ResponseEntity<ApiResponse<Long>> getProductWishlistCount(
            @PathVariable UUID productId) {
        long count = wishlistService.getProductWishlistCount(productId);

        return ResponseEntity.ok(
                ApiResponse.success("Product wishlist count retrieved successfully", count));
    }
}
