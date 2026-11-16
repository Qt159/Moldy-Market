package com.moldy.moldymarket.order.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.order.dto.request.CheckoutGroupRequest;
import com.moldy.moldymarket.order.dto.response.CheckoutResponse;
import com.moldy.moldymarket.order.service.CheckoutGroupService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checkout-groups")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckoutGroupController {
    CheckoutGroupService checkoutGroupService;

    @PostMapping
    ResponseEntity<ApiResponse<CheckoutResponse>> createCheckoutGroup(@Valid @RequestBody CheckoutGroupRequest request){
        return ResponseEntity.ok(ApiResponse.success(checkoutGroupService.createCheckoutGroup(request)));
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<CheckoutResponse>> getCheckoutById(@PathVariable UUID id){
        return ResponseEntity.ok(ApiResponse.success(checkoutGroupService.getCheckoutById(id)));
    }

}
