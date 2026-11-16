package com.moldy.moldymarket.wallet.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.wallet.enums.DisputeOutcome;
import com.moldy.moldymarket.wallet.dto.response.EscrowHoldResponse;
import com.moldy.moldymarket.wallet.service.EscrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Ký quỹ (Escrow)",
        description = "Quản lý tạm giữ, thu tiền và giải ngân tiền giao dịch đảm bảo")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/escrows")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EscrowController {
    EscrowService escrowService;

    @Operation(summary = "Thu tiền ký quỹ cho người bán",
            description = "Chốt số tiền ký quỹ theo đơn hàng đã hoàn tất")
    @PostMapping("/orders/{orderId}/capture")
    public ResponseEntity<ApiResponse<EscrowHoldResponse>> capture(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success("Escrow captured money to seller", escrowService.capture(orderId)));
    }

    @Operation(summary = "Giải ngân tiền ký quỹ cho người bán",
            description = "Admin chuyển số dư đang giữ về ví người mua để hoàn tiền")
    @PostMapping("/orders/{orderId}/release")
    public ResponseEntity<ApiResponse<EscrowHoldResponse>> release(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success("Escrow released to buyer", escrowService.release(orderId)));
    }

    @PostMapping("/orders/{orderId}/dispute")
    public ResponseEntity<ApiResponse<EscrowHoldResponse>> openDispute(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(escrowService.openDispute(orderId)));
    }

    @PostMapping("/orders/{orderId}/dispute/resolve")
    public ResponseEntity<ApiResponse<EscrowHoldResponse>> resolveDispute(
            @PathVariable String orderId, @RequestParam DisputeOutcome outcome) {
        return ResponseEntity.ok(ApiResponse.success(escrowService.resolveDispute(orderId, outcome)));
    }
}
