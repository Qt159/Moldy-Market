package com.moldy.moldymarket.commission.controller;

import com.moldy.moldymarket.commission.dto.response.AppraiserCommissionResponse;
import com.moldy.moldymarket.commission.enums.CommissionStatus;
import com.moldy.moldymarket.commission.service.AppraiserCommissionService;
import com.moldy.moldymarket.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

 
@RestController
@RequestMapping("/api/commissions")
@RequiredArgsConstructor
@Tag(name = "Commission", description = "Appraiser Commission Management")
public class CommissionController {
    private final AppraiserCommissionService commissionService;

    @Operation(summary = "Get commission by workflow ID")
    @GetMapping("/workflow/{workflowId}")
    public ResponseEntity<ApiResponse<AppraiserCommissionResponse>> getByWorkflowId(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(ApiResponse.success(commissionService.getByWorkflowId(workflowId)));
    }

    @Operation(summary = "Get appraiser's commissions")
    @GetMapping("/appraiser/{appraiserId}")
    public ResponseEntity<ApiResponse<Page<AppraiserCommissionResponse>>> getAppraiserCommissions(
            @PathVariable UUID appraiserId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(commissionService.getAppraiserCommissions(appraiserId, pageable)));
    }

    @Operation(summary = "Get appraiser's commissions by status")
    @GetMapping("/appraiser/{appraiserId}/status/{status}")
    public ResponseEntity<ApiResponse<Page<AppraiserCommissionResponse>>> getByStatus(
            @PathVariable UUID appraiserId,
            @PathVariable CommissionStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(commissionService.getAppraiserCommissionsByStatus(appraiserId, status, pageable)));
    }

    @Operation(summary = "Get appraiser total earnings")
    @GetMapping("/appraiser/{appraiserId}/earnings")
    public ResponseEntity<ApiResponse<Object>> getTotalEarnings(@PathVariable UUID appraiserId) {
        return ResponseEntity.ok(ApiResponse.success(commissionService.getTotalEarnings(appraiserId)));
    }

    @Operation(summary = "Mark commission as paid")
    @PatchMapping("/{commissionId}/mark-paid")
    public ResponseEntity<ApiResponse<AppraiserCommissionResponse>> markPaid(@PathVariable UUID commissionId) {
        return ResponseEntity.ok(ApiResponse.success(commissionService.markPaid(commissionId)));
    }
}
