package com.moldy.moldymarket.pricing.controller;

import com.moldy.moldymarket.common.ApiResponse;
import com.moldy.moldymarket.pricing.dto.ReferencePriceRequest;
import com.moldy.moldymarket.pricing.dto.ReferencePriceResponse;
import com.moldy.moldymarket.pricing.service.ReferencePriceService;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReferencePriceController {
    private final ReferencePriceService referencePriceService;

  
    @IsAdmin
    @GetMapping("/api/admin/reference-prices")
    public ResponseEntity<ApiResponse<Page<ReferencePriceResponse>>> getAllReferencePrices(
            Pageable pageable) {
        Page<ReferencePriceResponse> responses = referencePriceService.getAllReferencePrices(pageable);
        return ResponseEntity.ok(ApiResponse.success("Reference prices retrieved", responses));
    }

   
    @IsAdmin
    @GetMapping("/api/admin/reference-prices/{id}")
    public ResponseEntity<ApiResponse<ReferencePriceResponse>> getReferencePrice(
            @PathVariable UUID id) {
        ReferencePriceResponse response = referencePriceService.getReferencePrice(id);
        return ResponseEntity.ok(ApiResponse.success("Reference price retrieved", response));
    }

    
    @IsAdmin
    @PostMapping("/api/admin/reference-prices")
    public ResponseEntity<ApiResponse<ReferencePriceResponse>> createReferencePrice(
            @Valid @RequestBody ReferencePriceRequest request,
            Authentication authentication) {
        
        UUID adminId = extractUserIdFromAuthentication(authentication);
        ReferencePriceResponse response = referencePriceService.createReferencePrice(request, adminId);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Reference price created", response));
    }

   
    @IsAdmin
    @PutMapping("/api/admin/reference-prices/{id}")
    public ResponseEntity<ApiResponse<ReferencePriceResponse>> updateReferencePrice(
            @PathVariable UUID id,
            @Valid @RequestBody ReferencePriceRequest request,
            Authentication authentication) {
        
        UUID adminId = extractUserIdFromAuthentication(authentication);
        ReferencePriceResponse response = referencePriceService.updateReferencePrice(id, request, adminId);
        
        return ResponseEntity.ok(ApiResponse.success("Reference price updated", response));
    }

    
    @IsAdmin
    @DeleteMapping("/api/admin/reference-prices/{id}")
    public ResponseEntity<Void> deleteReferencePrice(@PathVariable UUID id) {
        referencePriceService.deleteReferencePrice(id);
        return ResponseEntity.noContent().build();
    }

    
    private UUID extractUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("Authentication required");
        }
        
        Object principal = authentication.getPrincipal();
        
        if (!(principal instanceof CustomUserDetails)) {
            throw new IllegalArgumentException("Invalid principal type: " + principal.getClass().getSimpleName()); }
        
        return ((CustomUserDetails) principal).getUserId();
    }
}
