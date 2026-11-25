package com.moldy.moldymarket.commission.controller;

import com.moldy.moldymarket.commission.dto.response.AppraiserCommissionResponse;
import com.moldy.moldymarket.commission.service.AppraiserCommissionService;
import com.moldy.moldymarket.security.PermissionConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/appraiser/commissions")
@PreAuthorize("hasAuthority('" + PermissionConstants.APPRAISAL_QUEUE_VIEW + "') and hasAuthority('" + PermissionConstants.WALLET_VIEW + "')")
@RequiredArgsConstructor
public class AppraiserCommissionController {

    private final AppraiserCommissionService commissionService;

    @GetMapping("/my")
    public ResponseEntity<List<AppraiserCommissionResponse>> getMyCommissions(
        Authentication authentication) {
        UUID appraiserId = getAppraiserId(authentication);

        return ResponseEntity.ok(
            commissionService.getMyCommissions(appraiserId));
    }

    @GetMapping("/{commissionId}")
    public ResponseEntity<AppraiserCommissionResponse> getMyCommission(
        @PathVariable UUID commissionId,
        Authentication authentication) {
        UUID appraiserId = getAppraiserId(authentication);

        return ResponseEntity.ok(
            commissionService.getMyCommission(commissionId, appraiserId));
    }

    private UUID getAppraiserId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Authenticated user ID must be a UUID"
            );
        }
    }
}