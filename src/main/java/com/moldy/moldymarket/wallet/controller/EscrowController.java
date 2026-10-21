package com.moldy.moldymarket.wallet.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.wallet.dto.request.EscrowHoldRequest;
import com.moldy.moldymarket.wallet.dto.response.EscrowHoldResponse;
import com.moldy.moldymarket.wallet.service.EscrowService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/escrows")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EscrowController {
    EscrowService escrowService;

    @PostMapping("/hold")
    public ResponseEntity<ApiResponse<EscrowHoldResponse>> hold(@RequestBody EscrowHoldRequest request){
        return ResponseEntity.ok(ApiResponse.success("Escrow is holding", escrowService.hold(request)));
    }

    @PostMapping("/orders/{orderId}/capture")
    public ResponseEntity<ApiResponse<EscrowHoldResponse>> capture(@PathVariable String orderId){
        return ResponseEntity.ok(ApiResponse.success("Escrow captured money to seller", escrowService.capture(orderId)));
    }
    @PostMapping("/orders/{orderId}/release")
    public ResponseEntity<ApiResponse<EscrowHoldResponse>> release(@PathVariable String orderId){
        return ResponseEntity.ok(ApiResponse.success("Escrow released money to seller", escrowService.release(orderId)));
    }
}
