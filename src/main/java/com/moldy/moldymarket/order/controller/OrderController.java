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

    // buyers
    @GetMapping("/me")
    ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrderByBuyer(){
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrderByBuyerId()));
    }

    @GetMapping("/buyer/{orderId}")
    ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable UUID orderId){
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(orderId)));
    }

    @PostMapping("/buyer/{orderId}/cancel")
    ResponseEntity<ApiResponse<OrderResponse>> cancelOrderFromBuyer(@PathVariable UUID orderId){
        return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrderFromBuyer(orderId)));
    }

    //sellers
    @PostMapping("/seller/{orderId}/confirm")
    ResponseEntity<ApiResponse<OrderResponse>> confirmOrder(@PathVariable UUID orderId){
        return ResponseEntity.ok(ApiResponse.success(orderService.confirmOrder(orderId)));
    }
    //get pendingConfirmed order

    //admin
    @IsAdmin
    @PatchMapping("/admin/{orderId}/start-shipping")
    public ResponseEntity<ApiResponse<OrderResponse>> startShipping(
            @PathVariable UUID orderId) {

        return ResponseEntity.ok(
                ApiResponse.success(orderService.startShipping(orderId))
        );
    }

    @IsAdmin
    @PatchMapping("/admin/{orderId}/mark-delivered")
    public ResponseEntity<ApiResponse<OrderResponse>> markDelivered(
            @PathVariable UUID orderId) {

        return ResponseEntity.ok(
                ApiResponse.success(orderService.markDelivered(orderId))
        );
    }


    @IsAdmin
    @PostMapping("/admin/{orderId}/cod-collected")
    public ResponseEntity<ApiResponse<OrderResponse>> receivedCOD(
            @PathVariable UUID orderId) {

        return ResponseEntity.ok(
                ApiResponse.success(orderService.receivedCOD(orderId))
        );
    }

}
