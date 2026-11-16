
package com.moldy.moldymarket.appraiser.controller;

import com.moldy.moldymarket.appraiser.dto.request.RegisterAppraiserRequest;
import com.moldy.moldymarket.appraiser.dto.request.UpdateAppraiserProfileRequest;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserProfileResponse;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserPublicProfileResponse;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserRegistrationResponse;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserStatusResponse;
import com.moldy.moldymarket.appraiser.service.AppraiserService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appraisers")
@RequiredArgsConstructor
@Tag(name = "Appraisers",
        description = "Đăng ký, quản lý hồ sơ và tra cứu chuyên gia thẩm định")
public class AppraiserController {

    private final AppraiserService appraiserService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đăng ký trở thành chuyên gia thẩm định")
    public ApiResponse<AppraiserRegistrationResponse> register(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RegisterAppraiserRequest request) {

        return ApiResponse.success(
                "Appraiser registration submitted successfully",
                appraiserService.register(userDetails.getUserId(), request)
        );
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem hồ sơ chuyên gia của tôi")
    public ApiResponse<AppraiserProfileResponse> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success("Appraiser profile retrieved successfully", appraiserService.getMyProfile(userDetails.getUserId()));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật hồ sơ chuyên gia")
    public ApiResponse<AppraiserProfileResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateAppraiserProfileRequest request) {

        return ApiResponse.success("Appraiser profile updated successfully", appraiserService.updateProfile(userDetails.getUserId(), request));
    }

    @GetMapping("/me/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem trạng thái hồ sơ của tôi")
    public ApiResponse<AppraiserStatusResponse> getMyStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ApiResponse.success("Appraiser status retrieved successfully", appraiserService.getStatusByUserId(userDetails.getUserId()));
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách chuyên gia đang hoạt động")
    public ApiResponse<Page<AppraiserPublicProfileResponse>> getActiveAppraisers(
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20) Pageable pageable) {

        return ApiResponse.success("Active appraisers retrieved successfully", appraiserService.getActiveAppraisers(categoryId, pageable));
    }

    @GetMapping("/{appraiserId}")
    @Operation(summary = "Xem hồ sơ công khai của chuyên gia")
    public ApiResponse<AppraiserPublicProfileResponse> getPublicProfile(
            @PathVariable UUID appraiserId) {

        return ApiResponse.success("Appraiser profile retrieved successfully", appraiserService.getPublicProfile(appraiserId));
    }

    @PatchMapping("/me/deactivate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ngừng hoạt động với tư cách chuyên gia")
    public ApiResponse<AppraiserStatusResponse> deactivate(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ApiResponse.success("Appraiser deactivated successfully", appraiserService.deactivate(userDetails.getUserId()));
    }

    @PatchMapping("/me/reactivate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kích hoạt lại hồ sơ chuyên gia")
    public ApiResponse<AppraiserStatusResponse> reactivate(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ApiResponse.success("Appraiser reactivated successfully", appraiserService.reactivate(userDetails.getUserId()));
    }
}
