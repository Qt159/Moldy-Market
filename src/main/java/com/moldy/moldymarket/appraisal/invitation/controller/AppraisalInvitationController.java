package com.moldy.moldymarket.appraisal.invitation.controller;

import com.moldy.moldymarket.appraisal.invitation.dto.request.CreateInvitationDto;
import com.moldy.moldymarket.appraisal.invitation.dto.response.AppraisalInvitationResponse;
import com.moldy.moldymarket.appraisal.invitation.service.AppraisalInvitationService;
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
public class AppraisalInvitationController {

    private final AppraisalInvitationService invitationService;


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
    @GetMapping("/appraisal-invitations/my-invitations")
    public ApiResponse<Page<AppraisalInvitationResponse>> getMyInvitations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(
                invitationService.getMyInvitations(userDetails.getUserId(), pageable));
    }

    
    @GetMapping("/appraisal-invitations/{invitationId}")
    public ApiResponse<AppraisalInvitationResponse> getById(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(
                    invitationService.getById(invitationId, userDetails.getUserId()));
    }

   
    @PatchMapping("/appraisal-invitations/{invitationId}/accept")
    public ApiResponse<Void> accept(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        invitationService.accept(invitationId, userDetails.getUserId());

        return ApiResponse.success(null);
    }

    
    @PatchMapping("/appraisal-invitations/{invitationId}/reject")
    public ApiResponse<Void> reject(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        invitationService.reject(invitationId, userDetails.getUserId());

        return ApiResponse.success(null);
    }

    
    @PatchMapping("/appraisal-invitations/{invitationId}/cancel")
    public ApiResponse<Void> cancel(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        invitationService.cancel(invitationId, userDetails.getUserId());

        return ApiResponse.success(null);
    }
}
