package com.moldy.moldymarket.appraisal.assignment.controller;

import com.moldy.moldymarket.appraisal.assignment.dto.request.SelectAppraiserDto;
import com.moldy.moldymarket.appraisal.assignment.dto.response.AppraisalAssignmentResponse;
import com.moldy.moldymarket.appraisal.assignment.service.AppraisalAssignmentService;
import com.moldy.moldymarket.common.dto.ApiResponse;
import com.moldy.moldymarket.security.annotation.IsAppraiser;
import com.moldy.moldymarket.security.annotation.IsUser;
import com.moldy.moldymarket.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appraisal-assignments")
@RequiredArgsConstructor
public class AppraisalAssignmentController {

    private final AppraisalAssignmentService assignmentService;

    @PostMapping("/select-appraiser")
    @IsUser
    public ResponseEntity<ApiResponse<AppraisalAssignmentResponse>> selectAppraiser(
            @RequestParam UUID requestId,
            @Valid @RequestBody SelectAppraiserDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AppraisalAssignmentResponse response =
                assignmentService.selectAppraiser(requestId, userDetails.getId(), dto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

 
    @GetMapping("/{assignmentId}")
    public ResponseEntity<ApiResponse<AppraisalAssignmentResponse>> getById(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AppraisalAssignmentResponse response =
                assignmentService.getById(assignmentId, userDetails.getId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my-assignments")
    @IsAppraiser
    public ResponseEntity<ApiResponse<Page<AppraisalAssignmentResponse>>> getMyAssignments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "assignedAt") Pageable pageable) {
        Page<AppraisalAssignmentResponse> response =
                assignmentService.getMyAssignments(userDetails.getId(), pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    
    @GetMapping("/seller/my-assignments")
    @IsUser
    public ResponseEntity<ApiResponse<Page<AppraisalAssignmentResponse>>> getSellerAssignments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "assignedAt") Pageable pageable) {
        Page<AppraisalAssignmentResponse> response =
                assignmentService.getSellerAssignments(userDetails.getId(), pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    
    @PatchMapping("/{assignmentId}/start")
    @IsAppraiser
    public ResponseEntity<ApiResponse<AppraisalAssignmentResponse>> start(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AppraisalAssignmentResponse response =
                assignmentService.start(assignmentId, userDetails.getId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    //Seller hoặc appraiser được cancel
    @PatchMapping("/{assignmentId}/cancel")
    public ResponseEntity<ApiResponse<AppraisalAssignmentResponse>> cancel(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AppraisalAssignmentResponse response =
                assignmentService.cancel(assignmentId, userDetails.getId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}