package com.moldy.moldymarket.user.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import com.moldy.moldymarket.user.dto.request.ChangePasswordRequest;
import com.moldy.moldymarket.user.dto.request.UpdateProfileRequest;
import com.moldy.moldymarket.user.dto.request.UpdateUserStatusRequest;
import com.moldy.moldymarket.user.dto.response.UserProfileReSponse;
import com.moldy.moldymarket.user.dto.response.UserSummaryResponse;
import com.moldy.moldymarket.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileReSponse>> getMyProfile(
            Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getMyProfile(authentication))
        );
    }

    // PROFILE_MANGAGE (ROLE USER, APPRAISER, OWNER đều có quyền này)
    @PreAuthorize("hasAuthority('" + PermissionConstants.PROFILE_MANAGE + "')")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileReSponse>> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.updateMyProfile(authentication, request))
        );
    }

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changPassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authentication, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_STORE_MANAGE + "')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileReSponse>> getUserById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getUserById(id))
        );
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_STORE_MANAGE + "')")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<UserSummaryResponse>>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getAllUsers(pageable))
        );
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_STORE_MANAGE + "')")
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserProfileReSponse>> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(userService.updateUserStatus(id, request))
        );
    }
}
