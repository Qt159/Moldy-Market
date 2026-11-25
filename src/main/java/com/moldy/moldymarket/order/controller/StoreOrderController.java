package com.moldy.moldymarket.order.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.dto.response.StoreOrderSummaryResponse;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.service.StoreOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/orders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class StoreOrderController {
    private final StoreOrderService storeOrderService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getStoreOrders(
            @PathVariable UUID storeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean oldestFirst) {
        return ResponseEntity.ok(ApiResponse.success(
                storeOrderService.getStoreOrders(storeId, page, size, status, q, from, to, oldestFirst)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<StoreOrderSummaryResponse>> getStoreOrderSummary(
            @PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.success(
                storeOrderService.getStoreOrderSummary(storeId)));
    }
}
