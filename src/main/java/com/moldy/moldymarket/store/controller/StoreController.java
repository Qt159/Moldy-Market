package com.moldy.moldymarket.store.controller;


import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.store.dto.request.CreateStoreRequest;
import com.moldy.moldymarket.store.dto.request.UpdateStoreRequest;
import com.moldy.moldymarket.store.dto.request.UpdateStoreStatusRequest;
import com.moldy.moldymarket.store.dto.response.StoreResponse;
import com.moldy.moldymarket.store.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> registerStore(
            Authentication authentication,
            @Valid @RequestBody CreateStoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(storeService.registerStore(authentication, request)));
    }

    // Chủ shop xem thông tin shop của mình
    @GetMapping("/my-store")
    public ResponseEntity<ApiResponse<StoreResponse>> getMyStore(
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.success(storeService.getMyStore(authentication)));
    }

    // Chủ shop cập nhật shop
    @PutMapping("/my-store")
    public ResponseEntity<ApiResponse<StoreResponse>> updateMyStore(
            Authentication authentication,
            @Valid @RequestBody UpdateStoreRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(storeService.updateMyStore(authentication, request)));
    }

    // Công khai: Ai cũng có thể xem thông tin shop
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreResponse>> getStoreById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(storeService.getStoreById(id)));
    }

    // Admin: Xem danh sách tất cả shop
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_STORE_MANAGE + "')")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<StoreResponse>>> getAllStores(
            @PageableDefault(size = 20, sort = "createdAt")Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(storeService.getAllStores(pageable)));
    }

    // Admin: Khóa hoặc mở khóa shop
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_STORE_MANAGE + "')")
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<StoreResponse>> updateStoreStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStoreStatusRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(storeService.updateStoreStatus(id, request)));
    }





}
