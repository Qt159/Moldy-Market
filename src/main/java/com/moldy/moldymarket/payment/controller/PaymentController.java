package com.moldy.moldymarket.payment.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.payment.dto.response.PaymentResponse;
import com.moldy.moldymarket.payment.dto.request.ConfirmPaymentRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import com.moldy.moldymarket.payment.service.PaymentService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {
    PaymentService paymentService;

//    @GetMapping("/orders/{orderId}")
//    ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(@PathVariable UUID orderId) {
//        return ResponseEntity.ok(ApiResponse.success(
//                paymentService.getPaymentByOrder(orderId)));
//    }

    @GetMapping("/admin/pending")
    ResponseEntity<ApiResponse<Slice<PaymentResponse>>> getPendingPayments(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.listPending(pageable)));
    }

    @PatchMapping("/admin/{id}/confirm")
    ResponseEntity<ApiResponse<PaymentResponse>> confirmTransfer(@PathVariable UUID id,
            @Valid @RequestBody ConfirmPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.confirmTransfer(id, request)));
    }

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
