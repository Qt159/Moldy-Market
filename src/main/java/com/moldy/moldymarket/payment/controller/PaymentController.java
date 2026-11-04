package com.moldy.moldymarket.payment.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.payment.dto.response.PaymentResponse;
import com.moldy.moldymarket.payment.service.PaymentService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {
    PaymentService paymentService;

    @PostMapping("/{orderId}")
    ResponseEntity<ApiResponse<PaymentResponse>> createPayment(@PathVariable UUID orderId,
                                                               Authentication authentication){
        return ResponseEntity.ok(ApiResponse.success(paymentService.createPayment(authentication, orderId)));
    }

    @GetMapping("/orders/{orderId}")
    ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(@PathVariable UUID orderId,
                                                                   Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getPaymentByOrder(authentication, orderId)));
    }

    @GetMapping("/my-payments")
    ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyPayments(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentByUser(authentication)));
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(@PathVariable UUID id,
                                                                Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getPaymentById(authentication, id)));
    }


}
