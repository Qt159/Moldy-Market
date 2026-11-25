package com.moldy.moldymarket.payment.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.payment.dto.response.PaymentResponse;
import com.moldy.moldymarket.payment.service.PaymentService;
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
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
@PreAuthorize("hasAnyAuthority('" + PermissionConstants.ORDER_PAY + "', '" + PermissionConstants.ORDER_TRACK + "')")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {
    PaymentService paymentService;

//    @GetMapping("/orders/{orderId}")
//    ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(@PathVariable UUID orderId) {
//        return ResponseEntity.ok(ApiResponse.success(
//                paymentService.getPaymentByOrder(orderId)));
//    }

    @GetMapping("/my-payments")
    ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyPayments() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentByUser()));
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getPaymentById(id)));
    }


}
