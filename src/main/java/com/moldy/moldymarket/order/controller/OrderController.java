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

    @IsAdmin
    @PostMapping("/admin/{orderId}/cod-collected")
    ResponseEntity<ApiResponse<OrderResponse>> receivedCOD(@PathVariable UUID orderId){
        return ResponseEntity.ok(ApiResponse.success(orderService.receivedCOD(orderId)));
    }


}
