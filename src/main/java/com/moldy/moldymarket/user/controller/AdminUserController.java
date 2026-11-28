package com.moldy.moldymarket.user.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.user.dto.request.AdminUserFilter;
import com.moldy.moldymarket.user.dto.request.LockUserRequest;
import com.moldy.moldymarket.user.dto.request.UnlockUserRequest;
import com.moldy.moldymarket.user.dto.response.AdminUserDetailResponse;
import com.moldy.moldymarket.user.dto.response.AdminUserSummaryResponse;
import com.moldy.moldymarket.user.dto.response.UserStatusHistoryResponse;
import com.moldy.moldymarket.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAuthority('" + PermissionConstants.USER_STORE_MANAGE + "')")
public class AdminUserController {
    AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AdminUserSummaryResponse>>> search(
            AdminUserFilter filter,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.searchUsers(filter, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUserDetail(id)));
    }

    @PatchMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> lock(
            @PathVariable UUID id,
            @Valid @RequestBody LockUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.lockUser(id, request)));
    }

    @PatchMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> unlock(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) UnlockUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.unlockUser(id, request)));
    }
    @GetMapping("/{id}/status-history")
    public ResponseEntity<ApiResponse<List<UserStatusHistoryResponse>>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getStatusHistory(id)));
    }
}
