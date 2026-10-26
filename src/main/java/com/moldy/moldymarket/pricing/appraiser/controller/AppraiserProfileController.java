package com.moldy.moldymarket.pricing.appraiser.controller;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserApplyRequest;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserProfileResponse;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserStatus;
import com.moldy.moldymarket.pricing.appraiser.service.AppraiserProfileService;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import com.moldy.moldymarket.security.annotation.IsUser;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Hồ sơ người định giá (Appraiser Profiles)", description = "Đăng ký, duyệt và xem thông tin người định giá")
@RestController
@RequiredArgsConstructor
public class AppraiserProfileController {

    private final AppraiserProfileService appraiserProfileService;

    @Operation(summary = "Đăng ký làm người định giá", description = "Người dùng nộp hồ sơ xin làm người định giá cho các danh mục chuyên môn")
    @IsUser
    @PostMapping("/api/pricing/appraiser/apply")
    public ResponseEntity<ApiResponse<AppraiserProfileResponse>> apply(
            @Valid @RequestBody AppraiserApplyRequest request,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        AppraiserProfileResponse response = appraiserProfileService.apply(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Appraiser application submitted", response));
    }

    @Operation(summary = "Xem hồ sơ người định giá của tôi", description = "Người định giá xem thông tin hồ sơ và điểm uy tín của mình")
    @GetMapping("/api/pricing/appraiser/me")
    public ResponseEntity<ApiResponse<AppraiserProfileResponse>> getMyProfile(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(appraiserProfileService.getMyProfile(userId)));
    }

    @Operation(summary = "Xem hồ sơ người định giá", description = "Xem chi tiết hồ sơ và điểm uy tín người định giá")
    @GetMapping("/api/pricing/appraiser/{id}")
    public ResponseEntity<ApiResponse<AppraiserProfileResponse>> getProfileById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(appraiserProfileService.getProfileById(id)));
    }

    @Operation(summary = "Danh sách người định giá theo danh mục", description = "Người bán tìm kiếm người định giá đang hoạt động trong danh mục")
    @GetMapping("/api/pricing/appraiser/list")
    public ResponseEntity<ApiResponse<List<AppraiserProfileResponse>>> getActiveAppraisers(
            @RequestParam(required = false) UUID categoryId) {
        return ResponseEntity.ok(ApiResponse.success(appraiserProfileService.getActiveAppraisersByCategory(categoryId)));
    }

    @Operation(summary = "Admin xem danh sách hồ sơ người định giá", description = "Admin xem danh sách hồ sơ với bộ lọc trạng thái và phân trang")
    @IsAdmin
    @GetMapping("/api/admin/appraisers")
    public ResponseEntity<ApiResponse<Page<AppraiserProfileResponse>>> getAllProfiles(
            @RequestParam(required = false) AppraiserStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(appraiserProfileService.getAllProfiles(pageable, status)));
    }

    @Operation(summary = "Admin duyệt hồ sơ người định giá", description = "Admin phê duyệt hồ sơ và cấp role APPRAISER")
    @IsAdmin
    @PutMapping("/api/admin/appraisers/{id}/approve")
    public ResponseEntity<ApiResponse<AppraiserProfileResponse>> approve(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID adminId = extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success("Appraiser profile approved", appraiserProfileService.approve(id, adminId)));
    }

    @Operation(summary = "Admin đình chỉ người định giá", description = "Admin đình chỉ hoạt động của người định giá")
    @IsAdmin
    @PutMapping("/api/admin/appraisers/{id}/suspend")
    public ResponseEntity<ApiResponse<AppraiserProfileResponse>> suspend(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID adminId = extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success("Appraiser profile suspended", appraiserProfileService.suspend(id, adminId)));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
}
