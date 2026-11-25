package com.moldy.moldymarket.offer.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.offer.dto.request.OfferCounterRequest;
import com.moldy.moldymarket.offer.dto.request.OfferCreationRequest;
import com.moldy.moldymarket.offer.dto.response.OfferResponse;
import com.moldy.moldymarket.offer.dto.response.PageResponse;
import com.moldy.moldymarket.offer.service.OfferService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OfferController {
    OfferService offerService;

    @PostMapping
    ResponseEntity<ApiResponse<OfferResponse>> createOffer(@Valid @RequestBody OfferCreationRequest request){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(offerService.createOffer(request)));
    }

    //create counter-offer
    @PostMapping("/{id}/counter")
    ResponseEntity<ApiResponse<OfferResponse>> counter(@PathVariable UUID id, @Valid @RequestBody OfferCounterRequest request){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(offerService.counter(id, request)));
    }

    //accept offer
    @PatchMapping("/{id}/accept")
    ResponseEntity<ApiResponse<OfferResponse>> accept(@PathVariable UUID id){
        return ResponseEntity.ok(ApiResponse.success(offerService.accept(id)));
    }
    //reject offer
    @PatchMapping("/{id}/reject")
    ResponseEntity<ApiResponse<OfferResponse>> reject(@PathVariable UUID id){
        return ResponseEntity.ok(ApiResponse.success(offerService.reject(id)));
    }

    //cancel negotiation
    @PatchMapping("/{id}/cancel")
    ResponseEntity<ApiResponse<OfferResponse>> cancel(@PathVariable UUID id){
        return ResponseEntity.ok(ApiResponse.success(offerService.cancel(id)));
    }

    @GetMapping("/sent")
    ResponseEntity<ApiResponse<PageResponse<OfferResponse>>> getSentOffers(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(offerService.getSentOffers(page, size)));
    }

    @GetMapping("/received")
    ResponseEntity<ApiResponse<PageResponse<OfferResponse>>> getReceivedOffers(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
            ) {
        return ResponseEntity.ok(
                ApiResponse.success(offerService.getReceivedOffers(page, size)));
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<OfferResponse>> getOffer(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(offerService.getOffer(id)));
    }

    @GetMapping("/{id}/history")
    ResponseEntity<ApiResponse<PageResponse<OfferResponse>>> getHistory(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(offerService.getHistory(page, size, id)));
    }
}
