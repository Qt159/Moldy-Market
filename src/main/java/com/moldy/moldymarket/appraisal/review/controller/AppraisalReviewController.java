package com.moldy.moldymarket.appraisal.review.controller;

import com.moldy.moldymarket.appraisal.review.dto.request.CreateAppraisalReviewDto;
import com.moldy.moldymarket.appraisal.review.dto.response.AppraisalReviewResponse;
import com.moldy.moldymarket.appraisal.review.service.AppraisalReviewService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.security.annotation.IsUser;
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
@RequestMapping("/api/v1/appraisal-reviews")
@RequiredArgsConstructor
public class AppraisalReviewController {

    private final AppraisalReviewService reviewService;

        //seller review
    @PostMapping("/assignments/{assignmentId}")
    @IsUser
    public ResponseEntity<ApiResponse<AppraisalReviewResponse>> create(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody CreateAppraisalReviewDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AppraisalReviewResponse response = reviewService.create(
                assignmentId,
                userDetails.getUserId(),
                dto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/assignments/{assignmentId}")
    public ResponseEntity<ApiResponse<AppraisalReviewResponse>> getByAssignment(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AppraisalReviewResponse response = reviewService.getByAssignment(
                assignmentId,
                userDetails.getUserId()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/appraisers/{appraiserId}")
    public ResponseEntity<ApiResponse<Page<AppraisalReviewResponse>>> getAppraiserReviews(
            @PathVariable UUID appraiserId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<AppraisalReviewResponse> response =
                reviewService.getAppraiserReviews(appraiserId, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

 
    @GetMapping("/my-reviews")
    @IsUser
    public ResponseEntity<ApiResponse<Page<AppraisalReviewResponse>>> getMyReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<AppraisalReviewResponse> response =
                reviewService.getMyReviews(userDetails.getUserId(), pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
