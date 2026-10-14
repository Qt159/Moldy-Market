package com.moldy.moldymarket.store.controller;


import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.store.dto.request.AddStaffRequest;
import com.moldy.moldymarket.store.dto.request.UpdateStaffPermissionsRequest;
import com.moldy.moldymarket.store.dto.response.StaffResponse;
import com.moldy.moldymarket.store.service.StoreStaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/my-store/staff")
@RequiredArgsConstructor
public class StoreStaffController {

    private final StoreStaffService storeStaffService;

    // Owner: Thêm nhân viên vào shop
    @PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
    @PostMapping
    public ResponseEntity<ApiResponse<StaffResponse>> addStaff(
            Authentication authentication,
            @Valid @RequestBody AddStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(storeStaffService.addStaff(authentication, request)));
    }

    // Owner: Xem danh sách nhân viên của shop
    @PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getStoreStaffList(
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.success(storeStaffService.getStoreStaffList(authentication)));
    }

    // Owner: Sửa quyền của nhân viên
    @PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
    @PutMapping("/{staffId}/permissions")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaffPermissions(
            Authentication authentication,
            @PathVariable UUID staffId,
            @Valid @RequestBody UpdateStaffPermissionsRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(storeStaffService.updateStaffPermissions(authentication, staffId, request)));
    }

    // Owner: Xóa nhân viên khỏi shop
    @PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
    @DeleteMapping("/{staffId}")
    public ResponseEntity<ApiResponse<Void>> removeStaff(
            Authentication authentication,
            @PathVariable UUID staffId) {
        storeStaffService.removeStaff(authentication, staffId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Staff: Nhân viên tự xem hồ sơ và các quyền được cấp của mình
    @PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StaffResponse>> getMyStaffProfile(
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.success(storeStaffService.getMyStaffProfile(authentication)));
    }
}
