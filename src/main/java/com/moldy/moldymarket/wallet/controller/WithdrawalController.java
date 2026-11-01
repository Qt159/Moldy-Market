package com.moldy.moldymarket.wallet.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.wallet.dto.request.RejectWithdrawal;
import com.moldy.moldymarket.wallet.dto.request.WithdrawalRequest;
import com.moldy.moldymarket.wallet.dto.response.WithdrawalResponse;
import com.moldy.moldymarket.wallet.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Rút tiền (Withdrawals)",
        description = "Yêu cầu và duyệt quy trình rút tiền về tài khoản ngân hàng")
@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WithdrawalController {
    WithdrawalService withdrawalService;

    @Operation(summary = "Tạo yêu cầu rút tiền",
            description = "Người dùng yêu cầu rút số dư ví về tài khoản ngân hàng")
    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalResponse>> requestWithdrawal(@Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Withdrawal request is created", withdrawalService.requestWithdrawal(request)));
    }

    @Operation(summary = "Duyệt yêu cầu rút tiền",
            description = "Admin phê duyệt yêu cầu rút tiền")
    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is approved", withdrawalService.approve(id)));
    }

    @Operation(summary = "Hoàn tất rút tiền",
            description = "Admin xác nhận đã chuyển khoản thành công cho người dùng")
    @PatchMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> complete(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is completed", withdrawalService.complete(id)));
    }

    @Operation(summary = "Từ chối rút tiền",
            description = "Admin từ chối yêu cầu rút tiền kèm lý do")
    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> reject(@PathVariable Long id, @Valid @RequestBody RejectWithdrawal rejectWithdrawal) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is rejected", withdrawalService.reject(id, rejectWithdrawal.reason())));
    }

    @Operation(summary = "Đánh dấu rút tiền thất bại",
            description = "Admin ghi nhận giao dịch chuyển khoản thất bại")
    @PatchMapping("/{id}/fail")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> fail(@PathVariable Long id, @Valid @RequestBody RejectWithdrawal rejectWithdrawal) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is failed", withdrawalService.fail(id, rejectWithdrawal.reason())));
    }
}
