package com.moldy.moldymarket.dispute.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.dispute.dto.request.DisputeCreationRequest;
import com.moldy.moldymarket.dispute.dto.request.ReviewDisputeRequest;
import com.moldy.moldymarket.dispute.dto.response.DisputeResponse;
import com.moldy.moldymarket.dispute.dto.response.DisputeSummaryResponse;
import com.moldy.moldymarket.dispute.service.DisputeService;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@ConditionalOnProperty(name = "features.disputes.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DisputeController {

    DisputeService disputeService;

    //buyers
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.DISPUTE_CREATE + "')")
    ResponseEntity<ApiResponse<DisputeResponse>> createDispute(@Valid @RequestBody DisputeCreationRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(disputeService.createDispute(request)));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.DISPUTE_CREATE + "', '" + PermissionConstants.DISPUTE_RESPOND + "')")
    ResponseEntity<ApiResponse<DisputeResponse>> getMyDispute(@PathVariable UUID orderId){
        return ResponseEntity.ok(ApiResponse.success(disputeService.getMyDispute(orderId)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.DISPUTE_CREATE + "', '" + PermissionConstants.DISPUTE_RESPOND + "')")
    ResponseEntity<ApiResponse<List<DisputeSummaryResponse>>> getAllMyDispute(){
        return ResponseEntity.ok(ApiResponse.success(disputeService.getAllMyDispute()));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('" + PermissionConstants.DISPUTE_CREATE + "')")
    ResponseEntity<ApiResponse<DisputeResponse>> cancel(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.cancelMyDispute(orderId)));
    }

    //admin
    @PreAuthorize("hasAuthority('" + PermissionConstants.DISPUTE_ADJUDICATE + "')")
    @GetMapping({"/admin", "/admin/"})
    ResponseEntity<ApiResponse<List<DisputeSummaryResponse>>> getDisputesForAdmin(){
        return ResponseEntity.ok(ApiResponse.success(disputeService.getDisputesForAdmin()));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.DISPUTE_ADJUDICATE + "')")
    @GetMapping("/admin/{id}")
    ResponseEntity<ApiResponse<DisputeResponse>> getDetailDisputeForAdmin(@PathVariable UUID id){
        return ResponseEntity.ok(ApiResponse.success(disputeService.getDetailDisputeForAdmin(id)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.DISPUTE_ADJUDICATE + "')")
    @PatchMapping("/admin/{id}/review")
    ResponseEntity<ApiResponse<DisputeResponse>> giveDecisionForDispute(@PathVariable UUID id, @Valid @RequestBody ReviewDisputeRequest request){
        return ResponseEntity.ok(ApiResponse.success(disputeService.giveDecisionForDispute(id, request)));
    }
}