package com.moldy.moldymarket.review.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.review.dto.CreateStoreReviewRequest;
import com.moldy.moldymarket.review.dto.StoreRatingSummaryResponse;
import com.moldy.moldymarket.review.dto.StoreReviewResponse;
import com.moldy.moldymarket.review.service.StoreReviewService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StoreReviewController {

    StoreReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<StoreReviewResponse>> createReview(
            @Valid @RequestBody CreateStoreReviewRequest request
            ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(reviewService.createReview(request)));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<StoreReviewResponse>> getReviewByOrder(
            @PathVariable UUID orderId
            ) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewByOrderId(orderId)));
    }

    @GetMapping("/stores/{storeId}")
    public ResponseEntity<ApiResponse<StoreRatingSummaryResponse>> getStoreReviews(
            @PathVariable UUID storeId
    ) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getStoreReviews(storeId)));
    }
}
