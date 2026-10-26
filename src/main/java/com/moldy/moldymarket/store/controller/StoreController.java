package com.moldy.moldymarket.store.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.store.dto.request.CreateStoreRequest;
import com.moldy.moldymarket.store.dto.request.UpdateStoreRequest;
import com.moldy.moldymarket.store.dto.request.UpdateStoreStatusRequest;
import com.moldy.moldymarket.store.dto.response.StoreResponse;
import com.moldy.moldymarket.store.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Cửa hàng (Stores)",
        description = "Đăng ký, quản lý thông tin và trạng thái cửa hàng")
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @Operation(summary = "Đăng ký mở cửa hàng",
            description = "Người dùng đăng ký mở một cửa hàng bán hàng mới")
    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> registerStore(
            Authentication authentication,
            @Valid @RequestBody CreateStoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(storeService.registerStore(authentication, request)));
    }

    @Operation(summary = "Xem thông tin cửa hàng của tôi",
            description = "Chủ shop xem thông tin chi tiết cửa hàng của mình")
    @GetMapping("/my-store")
    public ResponseEntity<ApiResponse<StoreResponse>> getMyStore(
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.success(storeService.getMyStore(authentication)));
    }

    @Operation(summary = "Cập nhật cửa hàng của tôi",
            description = "Chủ shop chỉnh sửa thông tin mô tả, logo, địa chỉ shop")
    @PutMapping("/my-store")
    public ResponseEntity<ApiResponse<StoreResponse>> updateMyStore(
            Authentication authentication,
            @Valid @RequestBody UpdateStoreRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(storeService.updateMyStore(authentication, request)));
    }

    @Operation(summary = "Xem chi tiết cửa hàng theo ID",
            description = "Công khai: Bất kỳ ai cũng có thể xem thông tin shop theo UUID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreResponse>> getStoreById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(storeService.getStoreById(id)));
    }

    @Operation(summary = "Danh sách tất cả cửa hàng",
            description = "Admin xem danh sách tất cả các shop có phân trang")
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_STORE_MANAGE + "')")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<StoreResponse>>> getAllStores(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(storeService.getAllStores(pageable)));
    }

    @Operation(summary = "Khóa hoặc mở khóa cửa hàng",
            description = "Admin cập nhật trạng thái hoạt động của cửa hàng")
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_STORE_MANAGE + "')")
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<StoreResponse>> updateStoreStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStoreStatusRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(storeService.updateStoreStatus(id, request)));
    }
}
