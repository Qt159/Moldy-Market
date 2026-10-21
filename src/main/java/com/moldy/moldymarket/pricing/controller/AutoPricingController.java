package com.moldy.moldymarket.pricing.controller;

import com.moldy.moldymarket.common.ApiResponse;
import com.moldy.moldymarket.pricing.dto.AutoPricingRequest;
import com.moldy.moldymarket.pricing.dto.AutoPricingResponse;
import com.moldy.moldymarket.pricing.service.AutoPricingService;
import com.moldy.moldymarket.security.annotation.IsUser;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;


@RestController
@RequiredArgsConstructor
public class AutoPricingController {
    private final AutoPricingService autoPricingService;

  
    @IsUser
    @PostMapping("/api/pricing/auto")
    public ResponseEntity<ApiResponse<AutoPricingResponse>> calculatePrice(
            @Valid @RequestBody AutoPricingRequest request, Authentication authentication) {
        
        UUID sellerId = extractUserIdFromAuthentication(authentication);
        AutoPricingResponse response = autoPricingService.calculatePrice(sellerId, request);

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("Auto pricing calculated", response));
    }

    

    private UUID extractUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("Authentication required"); }
    
        Object principal = authentication.getPrincipal();    
        if (!(principal instanceof CustomUserDetails)) {
            throw new IllegalArgumentException("Invalid principal type: " + principal.getClass().getSimpleName());
        }
        
        return ((CustomUserDetails) principal).getUserId();
    }
}
