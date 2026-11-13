package com.moldy.moldymarket.product.controller;

import com.moldy.moldymarket.common.ratelimit.RateLimitPolicy;
import com.moldy.moldymarket.common.ratelimit.RateLimiter;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.product.dto.*;
import com.moldy.moldymarket.product.entity.ProductStatus;
import com.moldy.moldymarket.product.service.ProductSearchService;
import com.moldy.moldymarket.product.service.ProductService;
import com.moldy.moldymarket.security.PermissionConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;
    private final RateLimiter rateLimiter;

    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_CREATE + "')")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(Authentication authentication,
                                                               @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(productService.create(authentication, request)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_MANAGE_OWN + "')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(Authentication authentication,
                                                               @PathVariable UUID id,
                                                               @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productService.update(authentication, id, request)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_MANAGE_OWN + "')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> changeStatus(Authentication authentication,
                                                          @PathVariable UUID id,
                                                          @RequestParam ProductStatus status) {
        productService.changeStatus(authentication, id, status);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_MANAGE_OWN + "')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
        productService.softDelete(authentication, id);
        return ResponseEntity.noContent().build();
    }

    // Public
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> search(
            ProductFilterRequest filter,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(productService.search(filter, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(Authentication authentication, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getById(authentication, id)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_MODERATE + "')")
    @PutMapping("/{id}/remove")
    public ResponseEntity<ApiResponse<ProductResponse>> remove(Authentication authentication,
                                                               @PathVariable UUID id,
                                                               @Valid @RequestBody RemoveProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product removed",
                productService.removeProduct(authentication, id, request)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_MODERATE + "')")
    @PutMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<ProductResponse>> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Product restored",
                productService.restoreProduct(id)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ProductSearchResponse>> search(
            @Valid ProductSearchRequest request, Pageable pageable, HttpServletRequest httpServletRequest) {
        rateLimiter.check(RateLimitPolicy.SEARCH_PER_IP, httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(productSearchService.search(request, pageable)));
    }

    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<String>>> suggest(
            @RequestParam(required = false) String q, HttpServletRequest httpServletRequest) {
                rateLimiter.check(RateLimitPolicy.SUGGEST_PER_IP, httpServletRequest.getRemoteAddr());
                return ResponseEntity.ok(ApiResponse.success(productSearchService.suggest(q)));
    }

}
