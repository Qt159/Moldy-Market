package com.moldy.moldymarket.pricing.appraiser.controller;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserPricingCreateRequest;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserPricingResponse;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserPricingSubmitRequest;
import com.moldy.moldymarket.pricing.appraiser.service.AppraiserPricingService;
import com.moldy.moldymarket.security.annotation.IsAppraiser;
import com.moldy.moldymarket.security.annotation.IsUser;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Định giá qua người định giá (Appraiser Pricing)", description = "Quy trình gửi yêu cầu, nhận việc, thẩm định và phản hồi giá")
@RestController
@RequiredArgsConstructor
public class AppraiserPricingController {

    private final AppraiserPricingService appraiserPricingService;

    @Operation(summary = "Gửi yêu cầu định giá", description = "Người bán gửi yêu cầu thẩm định giá sản phẩm tới người định giá hoặc hàng đợi")
    @IsUser
    @PostMapping("/api/pricing/appraiser/request")
    public ResponseEntity<ApiResponse<AppraiserPricingResponse>> createRequest(
            @Valid @RequestBody AppraiserPricingCreateRequest request,
            Authentication authentication) {
        UUID sellerId = extractUserId(authentication);
        AppraiserPricingResponse response = appraiserPricingService.createRequest(sellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Appraisal request created", response));
    }

    @Operation(summary = "Hàng đợi nhiệm vụ định giá", description = "Người định giá xem danh sách yêu cầu thẩm định đúng chuyên môn")
    @IsAppraiser
    @GetMapping("/api/pricing/appraiser/queue")
    public ResponseEntity<ApiResponse<List<AppraiserPricingResponse>>> getQueue(Authentication authentication) {
        UUID appraiserUserId = extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(appraiserPricingService.getAppraiserQueue(appraiserUserId)));
    }

    @Operation(summary = "Nhận nhiệm vụ định giá", description = "Người định giá nhận nhiệm vụ từ hàng đợi — khóa nhiệm vụ khỏi người khác")
    @IsAppraiser
    @PostMapping("/api/pricing/appraiser/{requestId}/claim")
    public ResponseEntity<ApiResponse<AppraiserPricingResponse>> claimTask(
            @PathVariable UUID requestId,
            Authentication authentication) {
        UUID appraiserUserId = extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success("Task claimed", appraiserPricingService.claimTask(appraiserUserId, requestId)));
    }

    @Operation(summary = "Gửi kết quả định giá", description = "Người định giá gửi giá đề xuất kèm nhận xét")
    @IsAppraiser
    @PostMapping("/api/pricing/appraiser/{requestId}/submit")
    public ResponseEntity<ApiResponse<AppraiserPricingResponse>> submitAppraisal(
            @PathVariable UUID requestId,
            @Valid @RequestBody AppraiserPricingSubmitRequest request,
            Authentication authentication) {
        UUID appraiserUserId = extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success("Appraisal submitted", appraiserPricingService.submitAppraisal(appraiserUserId, requestId, request)));
    }

    @Operation(summary = "Người bán chấp nhận giá đề xuất", description = "Người bán đồng ý với giá đề xuất của người định giá")
    @IsUser
    @PostMapping("/api/pricing/appraiser/{requestId}/accept")
    public ResponseEntity<ApiResponse<AppraiserPricingResponse>> acceptAppraisal(
            @PathVariable UUID requestId,
            Authentication authentication) {
        UUID sellerId = extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success("Appraisal accepted", appraiserPricingService.acceptAppraisal(sellerId, requestId)));
    }

    @Operation(summary = "Người bán từ chối giá đề xuất", description = "Người bán từ chối giá đề xuất (tối đa 3 lần/listing)")
    @IsUser
    @PostMapping("/api/pricing/appraiser/{requestId}/reject")
    public ResponseEntity<ApiResponse<AppraiserPricingResponse>> rejectAppraisal(
            @PathVariable UUID requestId,
            Authentication authentication) {
        UUID sellerId = extractUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success("Appraisal rejected", appraiserPricingService.rejectAppraisal(sellerId, requestId)));
    }

    @Operation(summary = "Chi tiết yêu cầu định giá", description = "Xem chi tiết một yêu cầu định giá")
    @IsUser
    @GetMapping("/api/pricing/requests/{requestId}")
    public ResponseEntity<ApiResponse<AppraiserPricingResponse>> getPricingRequest(@PathVariable UUID requestId) {
        return ResponseEntity.ok(ApiResponse.success(appraiserPricingService.getPricingRequest(requestId)));
    }

    @Operation(summary = "Xem kết quả định giá của listing", description = "Xem lịch sử/kết quả định giá của một listing")
    @IsUser
    @GetMapping("/api/pricing/{listingId}")
    public ResponseEntity<ApiResponse<List<AppraiserPricingResponse>>> getPricingByListing(@PathVariable UUID listingId) {
        return ResponseEntity.ok(ApiResponse.success(appraiserPricingService.getPricingByListingId(listingId)));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
}
