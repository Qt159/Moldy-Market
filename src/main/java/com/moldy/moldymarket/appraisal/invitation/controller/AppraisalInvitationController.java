package com.moldy.moldymarket.appraisal.invitation.controller;

import com.moldy.moldymarket.appraisal.invitation.dto.request.CreateInvitationDto;
import com.moldy.moldymarket.appraisal.invitation.dto.response.AppraisalInvitationResponse;
import com.moldy.moldymarket.appraisal.invitation.service.AppraisalInvitationService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AppraisalInvitationController {

    private final AppraisalInvitationService invitationService;


    @PreAuthorize("hasAuthority('" + PermissionConstants.APPRAISAL_REQUEST_CREATE + "')")
    @PostMapping("/appraisal-requests/{requestId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AppraisalInvitationResponse> create(
            @PathVariable UUID requestId,
            @Valid @RequestBody CreateInvitationDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(
                invitationService.create(requestId, userDetails.getUserId(), dto)
        );
    }

    // Seller xem invitations
    @PreAuthorize("hasAuthority('" + PermissionConstants.APPRAISAL_REQUEST_CREATE + "')")
    @GetMapping("/appraisal-requests/{requestId}/invitations")
    public ApiResponse<Page<AppraisalInvitationResponse>> getByRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(
                    invitationService.getByRequest(
                        requestId,
                        userDetails.getUserId(),
                        pageable));
    }

    //Appraiser xem
    @PreAuthorize("hasAuthority('" + PermissionConstants.APPRAISAL_QUEUE_VIEW + "')")
    @GetMapping("/appraisal-invitations/my-invitations")
    public ApiResponse<Page<AppraisalInvitationResponse>> getMyInvitations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(
                invitationService.getMyInvitations(userDetails.getUserId(), pageable));
    }

    
    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.APPRAISAL_REQUEST_CREATE + "', '" + PermissionConstants.APPRAISAL_QUEUE_VIEW + "')")
    @GetMapping("/appraisal-invitations/{invitationId}")
    public ApiResponse<AppraisalInvitationResponse> getById(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(
                    invitationService.getById(invitationId, userDetails.getUserId()));
    }

   
    @PreAuthorize("hasAuthority('" + PermissionConstants.APPRAISAL_TASK_ACCEPT + "')")
    @PatchMapping("/appraisal-invitations/{invitationId}/accept")
    public ApiResponse<Void> accept(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        invitationService.accept(invitationId, userDetails.getUserId());

        return ApiResponse.success(null);
    }

    
    @PreAuthorize("hasAuthority('" + PermissionConstants.APPRAISAL_TASK_ACCEPT + "')")
    @PatchMapping("/appraisal-invitations/{invitationId}/reject")
    public ApiResponse<Void> reject(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        invitationService.reject(invitationId, userDetails.getUserId());

        return ApiResponse.success(null);
    }

    
    @PreAuthorize("hasAuthority('" + PermissionConstants.APPRAISAL_REQUEST_CREATE + "')")
    @PatchMapping("/appraisal-invitations/{invitationId}/cancel")
    public ApiResponse<Void> cancel(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        invitationService.cancel(invitationId, userDetails.getUserId());

        return ApiResponse.success(null);
    }
}
