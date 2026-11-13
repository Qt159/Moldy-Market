
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

        return ApiResponse.success("Appraisers retrieved successfully", appraiserService.getAppraisers(status, pageable));
    }

    @GetMapping("/pending")
    @Operation(summary = "Lấy danh sách hồ sơ chờ duyệt")
    public ApiResponse<Page<AppraiserProfileResponse>> getPendingAppraisers(
            @PageableDefault(size = 20) Pageable pageable) {

        return ApiResponse.success("Pending appraisers retrieved successfully", appraiserService.getPendingAppraisers(pageable));
    }

    @GetMapping("/{appraiserId}/status")
    @Operation(summary = "Xem trạng thái chuyên gia thẩm định")
    public ApiResponse<AppraiserStatusResponse> getStatus(
            @PathVariable UUID appraiserId) {

        return ApiResponse.success("Appraiser status retrieved successfully", appraiserService.getStatus(appraiserId));
    }

    @PatchMapping("/{appraiserId}/approve")
    @Operation(summary = "Duyệt hồ sơ chuyên gia thẩm định")
    public ApiResponse<AppraiserStatusResponse> approve(
            @PathVariable UUID appraiserId) {

        return ApiResponse.success("Appraiser approved successfully", appraiserService.approveAppraiser(appraiserId));
    }

    @PatchMapping("/{appraiserId}/reject")
    @Operation(summary = "Từ chối hồ sơ chuyên gia thẩm định")
    public ApiResponse<AppraiserStatusResponse> reject(
            @PathVariable UUID appraiserId,
            @Valid @RequestBody RejectAppraiserRequest request) {

        return ApiResponse.success("Appraiser rejected successfully", appraiserService.rejectAppraiser(appraiserId, request));
    }

    @PatchMapping("/{appraiserId}/suspend")
    @Operation(summary = "Đình chỉ chuyên gia thẩm định")
    public ApiResponse<AppraiserStatusResponse> suspend(
            @PathVariable UUID appraiserId,
            @Valid @RequestBody SuspendAppraiserRequest request) {

        return ApiResponse.success("Appraiser suspended successfully", appraiserService.suspendAppraiser(appraiserId, request));
    }

    @PatchMapping("/{appraiserId}/resume")
    @Operation(summary = "Khôi phục chuyên gia thẩm định")
    public ApiResponse<AppraiserStatusResponse> resume(
            @PathVariable UUID appraiserId) {

        return ApiResponse.success("Appraiser resumed successfully", appraiserService.resumeAppraiser(appraiserId));
    }
}
