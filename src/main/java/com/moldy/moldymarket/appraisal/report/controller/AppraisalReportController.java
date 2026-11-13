package com.moldy.moldymarket.appraisal.report.controller;

import com.moldy.moldymarket.appraisal.report.dto.request.RejectAppraisalReportDto;
import com.moldy.moldymarket.appraisal.report.dto.request.SubmitAppraisalReportDto;
import com.moldy.moldymarket.appraisal.report.dto.response.AppraisalReportResponse;
import com.moldy.moldymarket.appraisal.report.service.AppraisalReportService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.security.annotation.IsAppraiser;
import com.moldy.moldymarket.security.annotation.IsUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appraisal-reports")
@RequiredArgsConstructor
public class AppraisalReportController {

    private final AppraisalReportService reportService;

    @PostMapping("/assignments/{assignmentId}")
    @IsAppraiser
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

    @GetMapping("/{reportId}")
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

    @GetMapping("/assignments/{assignmentId}/history")
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
    @IsUser
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
    @IsUser
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
