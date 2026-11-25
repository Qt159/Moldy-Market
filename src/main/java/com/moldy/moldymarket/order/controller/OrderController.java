package com.moldy.moldymarket.order.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.service.OrderService;
import com.moldy.moldymarket.security.PermissionConstants;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderController {
    OrderService orderService;

    @PreAuthorize("hasAuthority('" + PermissionConstants.ORDER_TRACK + "')")
    @GetMapping("/me")
    ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrderByBuyer(){
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrderByBuyerId()));
    }

    @PreAuthorize("hasAnyAuthority('" + PermissionConstants.ORDER_TRACK + "', '"
            + PermissionConstants.ORDER_PROCESS + "', '"
            + PermissionConstants.USER_STORE_MANAGE + "')")
    @GetMapping("/{orderId}")
    ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable UUID orderId){
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(orderId)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.ORDER_CANCEL + "')")
    @PostMapping("/buyer/{orderId}/cancel")
    ResponseEntity<ApiResponse<OrderResponse>> cancel(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrderFromBuyer(orderId)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.ORDER_CONFIRM_RECEIVED + "')")
    @PostMapping("/buyer/{orderId}/confirm-received")
    ResponseEntity<ApiResponse<OrderResponse>> confirmReceived(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.confirmReceivedByBuyer(orderId)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.ORDER_PROCESS + "')")
    @PostMapping("/seller/{orderId}/confirm")
    ResponseEntity<ApiResponse<OrderResponse>> confirm(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.confirmOrder(orderId)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.ADMIN_PORTAL_ACCESS + "')")
    @PatchMapping("/admin/{orderId}/start-shipping")
    ResponseEntity<ApiResponse<OrderResponse>> ship(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.startShipping(orderId)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.ADMIN_PORTAL_ACCESS + "')")
    @PatchMapping("/admin/{orderId}/mark-delivered")
    ResponseEntity<ApiResponse<OrderResponse>> deliver(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.markDelivered(orderId)));
    }

    @PreAuthorize("hasAuthority('" + PermissionConstants.ADMIN_PORTAL_ACCESS + "')")
    @PostMapping("/admin/{orderId}/cod-collected")
    ResponseEntity<ApiResponse<OrderResponse>> receivedCOD(@PathVariable UUID orderId){
        return ResponseEntity.ok(ApiResponse.success(orderService.receivedCOD(orderId)));
    }


}
