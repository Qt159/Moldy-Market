
package com.moldy.moldymarket.appraiser.controller;

import com.moldy.moldymarket.appraiser.dto.request.RejectAppraiserRequest;
import com.moldy.moldymarket.appraiser.dto.request.SuspendAppraiserRequest;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserProfileResponse;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserStatusResponse;
import com.moldy.moldymarket.appraiser.enums.AppraiserStatus;
import com.moldy.moldymarket.appraiser.service.AppraiserService;
import com.moldy.moldymarket.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/appraisers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Appraisers",
        description = "Quản lý và kiểm duyệt chuyên gia thẩm định")
public class AdminAppraiserController {
    private final AppraiserService appraiserService;

    @GetMapping
    @Operation(summary = "Lấy danh sách chuyên gia thẩm định")
    public ApiResponse<Page<AppraiserProfileResponse>> getAppraisers(
            @RequestParam(required = false) AppraiserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ApiResponse.success(
                appraiserService.getAppraisers(status, pageable),
                "Appraisers retrieved successfully");
    }

    @GetMapping("/pending")
    @Operation(summary = "Lấy danh sách hồ sơ chờ duyệt")
    public ApiResponse<Page<AppraiserProfileResponse>> getPendingAppraisers(
            @PageableDefault(size = 20) Pageable pageable) {

        return ApiResponse.success(
                appraiserService.getPendingAppraisers(pageable),
                "Pending appraisers retrieved successfully");
    }

    @GetMapping("/{appraiserId}/status")
    @Operation(summary = "Xem trạng thái chuyên gia thẩm định")
    public ApiResponse<AppraiserStatusResponse> getStatus(
            @PathVariable UUID appraiserId) {

        return ApiResponse.success(
                appraiserService.getStatus(appraiserId),
                "Appraiser status retrieved successfully");
    }

    @PatchMapping("/{appraiserId}/approve")
    @Operation(summary = "Duyệt hồ sơ chuyên gia thẩm định")
    public ApiResponse<AppraiserStatusResponse> approve(
            @PathVariable UUID appraiserId) {

        return ApiResponse.success(
                appraiserService.approveAppraiser(appraiserId),
                "Appraiser approved successfully");
    }

    @PatchMapping("/{appraiserId}/reject")
    @Operation(summary = "Từ chối hồ sơ chuyên gia thẩm định")
    public ApiResponse<AppraiserStatusResponse> reject(
            @PathVariable UUID appraiserId,
            @Valid @RequestBody RejectAppraiserRequest request) {

        return ApiResponse.success(
                appraiserService.rejectAppraiser(appraiserId, request),
                "Appraiser rejected successfully");
    }

    @PatchMapping("/{appraiserId}/suspend")
    @Operation(summary = "Đình chỉ chuyên gia thẩm định")
    public ApiResponse<AppraiserStatusResponse> suspend(
            @PathVariable UUID appraiserId,
            @Valid @RequestBody SuspendAppraiserRequest request) {

        return ApiResponse.success(
                appraiserService.suspendAppraiser(appraiserId, request),
                "Appraiser suspended successfully");
    }

    @PatchMapping("/{appraiserId}/resume")
    @Operation(summary = "Khôi phục chuyên gia thẩm định")
    public ApiResponse<AppraiserStatusResponse> resume(
            @PathVariable UUID appraiserId) {

        return ApiResponse.success(
                appraiserService.resumeAppraiser(appraiserId),
                "Appraiser resumed successfully");
    }
}
