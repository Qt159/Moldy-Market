package com.moldy.moldymarket.voucher.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import com.moldy.moldymarket.voucher.dto.CreateVoucherRequest;
import com.moldy.moldymarket.voucher.dto.VoucherCheckResponse;
import com.moldy.moldymarket.voucher.dto.VoucherResponse;
import com.moldy.moldymarket.voucher.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    //check: code, discountType, discountValue, expiredAt, valid (boolean).

    @GetMapping("/api/vouchers/{code}")
    public ResponseEntity<ApiResponse<VoucherCheckResponse>> checkVoucher(
            @PathVariable String code
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(voucherService.checkVoucher(code))
        );
    }


    @IsAdmin
    @PostMapping("/api/admin/vouchers")
    public ResponseEntity<ApiResponse<VoucherResponse>> createVoucher(
            @Valid @RequestBody CreateVoucherRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(
                "Voucher created successfully",
                voucherService.createVoucher(request)));
    }


    @IsAdmin
    @GetMapping("/api/admin/vouchers")
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getAllVouchers() {
        return ResponseEntity.ok(
            ApiResponse.success(voucherService.getAllVouchers())
        );
    }

 
    @IsAdmin
    @GetMapping("/api/admin/vouchers/{code}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getVoucherByCode(
            @PathVariable String code) {
        return ResponseEntity.ok(
            ApiResponse.success(voucherService.getVoucherByCode(code))
        );
    }
}
