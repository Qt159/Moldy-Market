package com.moldy.moldymarket.wallet.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.wallet.dto.request.RejectWithdrawal;
import com.moldy.moldymarket.wallet.dto.request.WithdrawalRequest;
import com.moldy.moldymarket.wallet.dto.response.WithdrawalResponse;
import com.moldy.moldymarket.wallet.service.WithdrawalService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WithdrawalController {
    WithdrawalService withdrawalService;

    @PostMapping
    ResponseEntity<ApiResponse<WithdrawalResponse>> requestWithdrawal(@Valid @RequestBody WithdrawalRequest request){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Withdrawal request is created", withdrawalService.requestWithdrawal(request)));
    }

    @PatchMapping("/{id}/approve")
    ResponseEntity<ApiResponse<WithdrawalResponse>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is approved", withdrawalService.approve(id)));
    }

    @PatchMapping("/{id}/complete")
    ResponseEntity<ApiResponse<WithdrawalResponse>> complete(@PathVariable Long id){
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is completed", withdrawalService.complete(id)));
    }

    @PatchMapping("/{id}/reject")
    ResponseEntity<ApiResponse<WithdrawalResponse>> reject(@PathVariable Long id, @Valid @RequestBody RejectWithdrawal rejectWithdrawal) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is rejected", withdrawalService.reject(id, rejectWithdrawal.reason())));
    }

    @PatchMapping("/{id}/fail")
    ResponseEntity<ApiResponse<WithdrawalResponse>> fail(@PathVariable Long id, @Valid @RequestBody RejectWithdrawal rejectWithdrawal) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is failed", withdrawalService.fail(id, rejectWithdrawal.reason())));
    }
}
