package com.moldy.moldymarket.order.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.order.dto.request.OrderCreationRequest;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.service.OrderService;
import jakarta.validation.Valid;
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

    @PostMapping
    ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody OrderCreationRequest request){
        return ResponseEntity.ok(ApiResponse.success(orderService.createOrder(request)));
    }

    @GetMapping("/me")
    ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrderByBuyer(){
        return ResponseEntity.ok(ApiResponse.success(orderService.getAllOrderByBuyerId()));
    }

    @GetMapping("/{orderId}")
    ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable UUID orderId){
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(orderId)));
    }

}
