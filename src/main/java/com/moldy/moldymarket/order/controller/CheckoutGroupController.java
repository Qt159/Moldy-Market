package com.moldy.moldymarket.order.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.order.dto.request.CheckoutGroupRequest;
import com.moldy.moldymarket.order.dto.response.CheckoutResponse;
import com.moldy.moldymarket.order.service.CheckoutGroupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout-groups")
public class CheckoutGroupController {
    CheckoutGroupService checkoutGroupService;

    @PostMapping
    ResponseEntity<ApiResponse<CheckoutResponse>> createCheckoutGroup(@Valid @RequestBody CheckoutGroupRequest request){
        return ResponseEntity.ok(ApiResponse.success(checkoutGroupService.createCheckoutGroup(request)));
    }

}
