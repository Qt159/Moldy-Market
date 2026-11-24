package com.moldy.moldymarket.order.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.service.OrderService;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderController {
    OrderService orderService;

    @GetMapping("/me")
    ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrderByBuyer(){
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrderByBuyerId()));
    }

    @GetMapping("/{orderId}")
    ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable UUID orderId){
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(orderId)));
    }

    @PostMapping("/buyer/{orderId}/cancel")
    ResponseEntity<ApiResponse<OrderResponse>> cancel(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrderFromBuyer(orderId)));
    }

    @PostMapping("/buyer/{orderId}/confirm-received")
    ResponseEntity<ApiResponse<OrderResponse>> confirmReceived(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.confirmReceivedByBuyer(orderId)));
    }

    @PostMapping("/seller/{orderId}/confirm")
    ResponseEntity<ApiResponse<OrderResponse>> confirm(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.confirmOrder(orderId)));
    }

    @IsAdmin
    @PatchMapping("/admin/{orderId}/start-shipping")
    ResponseEntity<ApiResponse<OrderResponse>> ship(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.startShipping(orderId)));
    }

    @IsAdmin
    @PatchMapping("/admin/{orderId}/mark-delivered")
    ResponseEntity<ApiResponse<OrderResponse>> deliver(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.markDelivered(orderId)));
    }

    @IsAdmin
    @PostMapping("/admin/{orderId}/cod-collected")
    ResponseEntity<ApiResponse<OrderResponse>> receivedCOD(@PathVariable UUID orderId){
        return ResponseEntity.ok(ApiResponse.success(orderService.receivedCOD(orderId)));
    }


}
