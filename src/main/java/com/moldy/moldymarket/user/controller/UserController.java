package com.moldy.moldymarket.user.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.user.dto.request.ChangePasswordRequest;
import com.moldy.moldymarket.user.dto.request.UpdateProfileRequest;
import com.moldy.moldymarket.user.dto.request.UpdateUserStatusRequest;
import com.moldy.moldymarket.user.dto.response.AdminUserDetailResponse;
import com.moldy.moldymarket.user.dto.response.UserProfileReSponse;
import com.moldy.moldymarket.user.dto.response.UserSummaryResponse;
import com.moldy.moldymarket.user.service.AdminUserService;
import com.moldy.moldymarket.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Người dùng (Users)", description = "Quản lý thông tin cá nhân và tài khoản người dùng")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AdminUserService adminUserService;

    @Operation(summary = "Xem hồ sơ của tôi", description = "Lấy thông tin tài khoản hiện tại. avatarUrl của avatar đã upload là URL ổn định /api/v1/files/{fileId}/view.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileReSponse>> getMyProfile(
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getMyProfile(authentication))
        );
    }

    // PROFILE_MANGAGE (ROLE USER, APPRAISER, OWNER đều có quyền này)
    @Operation(summary = "Cập nhật hồ sơ cá nhân", description = "Gửi avatarFileId sau khi file USER_AVATAR đã confirm. avatarUrl legacy vẫn được chấp nhận; gửi avatarUrl rỗng để gỡ avatar. avatarFileId phải thuộc user hiện tại và ở trạng thái AVAILABLE.")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PROFILE_MANAGE + "')")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileReSponse>> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.updateMyProfile(authentication, request))
        );
    }

    @Operation(summary = "Đổi mật khẩu", description = "Đổi mật khẩu đăng nhập của tài khoản hiện tại")
    @PreAuthorize("hasAuthority('" + PermissionConstants.PROFILE_MANAGE + "')")
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authentication, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Xem người dùng theo ID", description = "Admin xem chi tiết hồ sơ người dùng theo UUID")
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_STORE_MANAGE + "')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileReSponse>> getUserById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getUserById(id))
        );
    }

    @Operation(summary = "Danh sách người dùng", description = "Admin lấy danh sách tất cả người dùng có phân trang")
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_STORE_MANAGE + "')")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<UserSummaryResponse>>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getAllUsers(pageable))
        );
    }

    @Deprecated
    @Operation(summary = "[Cũ] Cập nhật trạng thái người dùng",
                        description = "Đã thay bằng /api/v1/admin/users/{id}/lock và /unlock. Chỉ chấp nhận LOCKED hoặc ACTIVE, cùng quy tắc với API mới.")
    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_STORE_MANAGE + "')")
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(adminUserService.changeStatusLegacy(id, request.status()))
        );
    }
}
