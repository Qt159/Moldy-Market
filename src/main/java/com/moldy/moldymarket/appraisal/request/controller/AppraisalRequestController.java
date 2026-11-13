package com.moldy.moldymarket.appraisal.request.controller;

import com.moldy.moldymarket.appraisal.request.dto.request.CreateAppraisalRequestDto;
import com.moldy.moldymarket.appraisal.request.dto.response.AppraisalRequestResponse;
import com.moldy.moldymarket.appraisal.request.service.AppraisalRequestService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appraisal-requests")
@RequiredArgsConstructor
public class AppraisalRequestController {

    private final AppraisalRequestService requestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AppraisalRequestResponse> create(
            @Valid @RequestBody CreateAppraisalRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(
                requestService.create(dto, userDetails.getId()));
    }

    @GetMapping("/{requestId}")
    public ApiResponse<AppraisalRequestResponse> getById(@PathVariable UUID requestId) {
        return ApiResponse.success(requestService.getById(requestId));
    }

    @GetMapping("/my-requests")
    public ApiResponse<Page<AppraisalRequestResponse>> getMyRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(
                requestService.getMyRequests(userDetails.getId(), pageable));
    }

    @GetMapping("/market/open")
    public ApiResponse<Page<AppraisalRequestResponse>> getOpenRequests(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(requestService.getOpenRequests(pageable));
    }

    @PatchMapping("/{requestId}/cancel")
    public ApiResponse<Void> cancel(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requestService.cancel(requestId, userDetails.getId());

        return ApiResponse.success(null);
    }
}
