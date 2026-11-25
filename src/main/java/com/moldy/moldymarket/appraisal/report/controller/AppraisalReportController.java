package com.moldy.moldymarket.appraisal.report.controller;

import com.moldy.moldymarket.appraisal.report.dto.request.RejectAppraisalReportDto;
import com.moldy.moldymarket.appraisal.report.dto.request.SubmitAppraisalReportDto;
import com.moldy.moldymarket.appraisal.report.dto.response.AppraisalReportResponse;
import com.moldy.moldymarket.appraisal.report.service.AppraisalReportService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appraisal-reports")
@RequiredArgsConstructor
public class AppraisalReportController {

    private final AppraisalReportService reportService;

    @PostMapping("/assignments/{assignmentId}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.APPRAISAL_SUBMIT + "')")
    @Operation(summary = "Submit appraisal report", description = "Use mediaFileIds for confirmed APPRAISAL_EVIDENCE uploads owned by the assigned appraiser. Response includes expiring viewUrl values.")
    public ResponseEntity<ApiResponse<AppraisalReportResponse>> submit(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody SubmitAppraisalReportDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AppraisalReportResponse response = reportService.submit(
                assignmentId,
                userDetails.getUserId(),
                dto
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.APPRAISAL_REQUEST_CREATE + "', '"
            + PermissionConstants.APPRAISAL_SUBMIT + "', '"
            + PermissionConstants.APPRAISAL_QUEUE_VIEW + "')")
    @GetMapping("/{reportId}")
    @Operation(summary = "Get appraisal report", description = "Only the assigned appraiser and seller participant may read report media.")
    public ResponseEntity<ApiResponse<AppraisalReportResponse>> getById(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AppraisalReportResponse response = reportService.getById(
                reportId,
                userDetails.getUserId()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.APPRAISAL_REQUEST_CREATE + "', '"
            + PermissionConstants.APPRAISAL_SUBMIT + "', '"
            + PermissionConstants.APPRAISAL_QUEUE_VIEW + "')")
    @GetMapping("/assignments/{assignmentId}/history")
    @Operation(summary = "Get appraisal history", description = "Only the assigned appraiser and seller participant may read report media.")
    public ResponseEntity<ApiResponse<List<AppraisalReportResponse>>> getHistory(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<AppraisalReportResponse> response = reportService.getHistory(
                assignmentId,
                userDetails.getUserId()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{reportId}/accept")
    @PreAuthorize("hasAuthority('" + PermissionConstants.APPRAISAL_REQUEST_CREATE + "')")
    public ResponseEntity<ApiResponse<AppraisalReportResponse>> accept(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AppraisalReportResponse response = reportService.accept(
                reportId,
                userDetails.getUserId()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{reportId}/reject")
    @PreAuthorize("hasAuthority('" + PermissionConstants.APPRAISAL_REQUEST_CREATE + "')")
    public ResponseEntity<ApiResponse<AppraisalReportResponse>> reject(
            @PathVariable UUID reportId,
            @Valid @RequestBody RejectAppraisalReportDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AppraisalReportResponse response = reportService.reject(
                reportId,
                userDetails.getUserId(),
                dto.rejectionReason()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
