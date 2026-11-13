package com.moldy.moldymarket.appraisal.application.controller;

import com.moldy.moldymarket.appraisal.application.dto.request.CreateApplicationDto;
import com.moldy.moldymarket.appraisal.application.dto.response.AppraiserApplicationResponse;
import com.moldy.moldymarket.appraisal.application.service.AppraiserApplicationService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.user.CustomUserDetails;
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
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AppraiserApplicationController {

    private final AppraiserApplicationService applicationService;

    @PostMapping("/appraisal-requests/{requestId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AppraiserApplicationResponse> apply(
            @PathVariable UUID requestId,
            @Valid @RequestBody CreateApplicationDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(
                applicationService.apply(requestId, userDetails.getUserId(), dto));
    }

    
    @GetMapping("/appraisal-requests/{requestId}/applications")
    public ApiResponse<Page<AppraiserApplicationResponse>> getByRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(
                applicationService.getByRequest(requestId, userDetails.getUserId(), pageable));
    }

    
    @GetMapping("/appraisal-applications/my-applications")
    public ApiResponse<Page<AppraiserApplicationResponse>> getMyApplications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(
                applicationService.getMyApplications(
                                    userDetails.getUserId(), pageable));
    }

    
    @GetMapping("/appraisal-applications/{applicationId}")
    public ApiResponse<AppraiserApplicationResponse> getById(
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(
                applicationService.getById(
                        applicationId, userDetails.getUserId()));
    }

    
    @PatchMapping("/appraisal-applications/{applicationId}/withdraw")
    public ApiResponse<Void> withdraw(
            @PathVariable UUID applicationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        applicationService.withdraw(applicationId, userDetails.getUserId());

        return ApiResponse.success(null);
    }
}
