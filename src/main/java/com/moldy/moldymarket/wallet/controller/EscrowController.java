package com.moldy.moldymarket.wallet.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.wallet.dto.request.EscrowHoldRequest;
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

    @Operation(summary = "Tạm giữ tiền ký quỹ",
            description = "Tạm giữ tiền thanh toán của người mua vào tài khoản ký quỹ")
    @PostMapping("/hold")
    public ResponseEntity<ApiResponse<EscrowHoldResponse>> hold(@RequestBody EscrowHoldRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Escrow is holding", escrowService.hold(request)));
    }

    @Operation(summary = "Thu tiền ký quỹ cho người bán",
            description = "Chốt số tiền ký quỹ theo đơn hàng đã hoàn tất")
    @PostMapping("/orders/{orderId}/capture")
    public ResponseEntity<ApiResponse<EscrowHoldResponse>> capture(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success("Escrow captured money to seller", escrowService.capture(orderId)));
    }

    @Operation(summary = "Giải ngân tiền ký quỹ cho người bán",
            description = "Giải phóng số tiền đang tạm giữ chuyển về ví người bán")
    @PostMapping("/orders/{orderId}/release")
    public ResponseEntity<ApiResponse<EscrowHoldResponse>> release(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success("Escrow released money to seller", escrowService.release(orderId)));
    }
}
