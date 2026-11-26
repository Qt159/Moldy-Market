package com.moldy.moldymarket.offer.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.offer.dto.request.OfferCounterRequest;
import com.moldy.moldymarket.offer.dto.request.OfferCreationRequest;
import com.moldy.moldymarket.offer.dto.response.OfferResponse;
import com.moldy.moldymarket.offer.dto.response.PageResponse;
import com.moldy.moldymarket.offer.service.OfferService;
import com.moldy.moldymarket.security.PermissionConstants;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OfferController {
    OfferService offerService;

    @PreAuthorize("hasAuthority('" + PermissionConstants.OFFER_CREATE + "')")
    @PostMapping
    ResponseEntity<ApiResponse<OfferResponse>> createOffer(@Valid @RequestBody OfferCreationRequest request){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(offerService.createOffer(request)));
    }

    //create counter-offer
    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.OFFER_CREATE + "', '" + PermissionConstants.OFFER_HANDLE + "')")
    @PostMapping("/{id}/counter")
    ResponseEntity<ApiResponse<OfferResponse>> counter(@PathVariable UUID id, @Valid @RequestBody OfferCounterRequest request){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(offerService.counter(id, request)));
    }

    //accept offer
    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.OFFER_CREATE + "', '" + PermissionConstants.OFFER_HANDLE + "')")
    @PatchMapping("/{id}/accept")
    ResponseEntity<ApiResponse<OfferResponse>> accept(@PathVariable UUID id){
        return ResponseEntity.ok(ApiResponse.success(offerService.accept(id)));
    }
    //reject offer
    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.OFFER_CREATE + "', '" + PermissionConstants.OFFER_HANDLE + "')")
    @PatchMapping("/{id}/reject")
    ResponseEntity<ApiResponse<OfferResponse>> reject(@PathVariable UUID id){
        return ResponseEntity.ok(ApiResponse.success(offerService.reject(id)));
    }

    //cancel negotiation
    @PreAuthorize("hasAuthority('" + PermissionConstants.OFFER_CREATE + "')")
    @PatchMapping("/{id}/cancel")
    ResponseEntity<ApiResponse<OfferResponse>> cancel(@PathVariable UUID id){
        return ResponseEntity.ok(ApiResponse.success(offerService.cancel(id)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.OFFER_CREATE + "')")
    @GetMapping("/sent")
    ResponseEntity<ApiResponse<PageResponse<OfferResponse>>> getSentOffers(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(offerService.getSentOffers(page, size)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.OFFER_HANDLE + "')")
    @GetMapping("/received")
    ResponseEntity<ApiResponse<PageResponse<OfferResponse>>> getReceivedOffers(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
            ) {
        return ResponseEntity.ok(
                ApiResponse.success(offerService.getReceivedOffers(page, size)));
    }

    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.OFFER_CREATE + "', '" + PermissionConstants.OFFER_HANDLE + "')")
    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<OfferResponse>> getOffer(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(offerService.getOffer(id)));
    }

    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.OFFER_CREATE + "', '" + PermissionConstants.OFFER_HANDLE + "')")
    @GetMapping("/{id}/history")
    ResponseEntity<ApiResponse<PageResponse<OfferResponse>>> getHistory(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(offerService.getHistory(page, size, id)));
    }
}
