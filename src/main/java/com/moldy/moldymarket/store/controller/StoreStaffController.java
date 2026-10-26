package com.moldy.moldymarket.store.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.store.dto.request.AddStaffRequest;
import com.moldy.moldymarket.store.dto.request.UpdateStaffPermissionsRequest;
import com.moldy.moldymarket.store.dto.response.StaffResponse;
import com.moldy.moldymarket.store.service.StoreStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Nhân viên cửa hàng (Store Staff)",
        description = "Quản lý nhân viên và phân quyền trong cửa hàng")
@RestController
@RequestMapping("/api/v1/stores/my-store/staff")
@RequiredArgsConstructor
public class StoreStaffController {

    private final StoreStaffService storeStaffService;

    @Operation(summary = "Thêm nhân viên vào shop",
            description = "Chủ shop tuyển nhân viên và gán quyền ban đầu")
    @PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
    @PostMapping
    public ResponseEntity<ApiResponse<StaffResponse>> addStaff(
            Authentication authentication,
            @Valid @RequestBody AddStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(storeStaffService.addStaff(authentication, request)));
    }

    @Operation(summary = "Danh sách nhân viên của shop",
            description = "Chủ shop xem danh sách toàn bộ nhân viên cửa hàng")
    @PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getStoreStaffList(
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.success(storeStaffService.getStoreStaffList(authentication)));
    }

    @Operation(summary = "Cập nhật quyền của nhân viên",
            description = "Chủ shop thay đổi danh sách quyền hạn cho nhân viên")
    @PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
    @PutMapping("/{staffId}/permissions")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaffPermissions(
            Authentication authentication,
            @PathVariable UUID staffId,
            @Valid @RequestBody UpdateStaffPermissionsRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(storeStaffService.updateStaffPermissions(authentication, staffId, request)));
    }

    @Operation(summary = "Xóa nhân viên khỏi shop",
            description = "Chủ shop thu hồi quyền và xóa nhân viên khỏi cửa hàng")
    @PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
    @DeleteMapping("/{staffId}")
    public ResponseEntity<ApiResponse<Void>> removeStaff(
            Authentication authentication,
            @PathVariable UUID staffId) {
        storeStaffService.removeStaff(authentication, staffId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Xem hồ sơ nhân viên của tôi",
            description = "Nhân viên tự xem thông tin và danh sách quyền hạn của mình")
    @PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StaffResponse>> getMyStaffProfile(
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.success(storeStaffService.getMyStaffProfile(authentication)));
    }
}
