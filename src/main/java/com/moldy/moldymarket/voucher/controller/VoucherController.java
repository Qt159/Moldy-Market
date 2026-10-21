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

<<<<<<< HEAD
=======
/**
 * Voucher management REST controller.
 * - Check voucher: Public (no auth)
 * - Create/List/Get: Admin only
 */
>>>>>>> origin/feature/voucher
@RestController
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

<<<<<<< HEAD
    //check: code, discountType, discountValue, expiredAt, valid (boolean).

=======
    /**
     * Check voucher validity (public).
     * Returns: code, discountType, discountValue, expiredAt, valid (boolean).
     */
>>>>>>> origin/feature/voucher
    @GetMapping("/api/vouchers/{code}")
    public ResponseEntity<ApiResponse<VoucherCheckResponse>> checkVoucher(
            @PathVariable String code
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(voucherService.checkVoucher(code))
        );
    }

<<<<<<< HEAD

    @IsAdmin
    @PostMapping("/api/admin/vouchers")
    public ResponseEntity<ApiResponse<VoucherResponse>> createVoucher(
            @Valid @RequestBody CreateVoucherRequest request) {
=======
    /**
     * Create a new voucher (admin only).
     */
    @IsAdmin
    @PostMapping("/api/admin/vouchers")
    public ResponseEntity<ApiResponse<VoucherResponse>> createVoucher(
            @Valid @RequestBody CreateVoucherRequest request
    ) {
>>>>>>> origin/feature/voucher
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(
                "Voucher created successfully",
<<<<<<< HEAD
                voucherService.createVoucher(request)));
    }


=======
                voucherService.createVoucher(request)
            ));
    }

    /**
     * Get all vouchers (admin only).
     */
>>>>>>> origin/feature/voucher
    @IsAdmin
    @GetMapping("/api/admin/vouchers")
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getAllVouchers() {
        return ResponseEntity.ok(
            ApiResponse.success(voucherService.getAllVouchers())
        );
    }

<<<<<<< HEAD
 
    @IsAdmin
    @GetMapping("/api/admin/vouchers/{code}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getVoucherByCode(
            @PathVariable String code) {
=======
    /**
     * Get voucher by code (admin only).
     */
    @IsAdmin
    @GetMapping("/api/admin/vouchers/{code}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getVoucherByCode(
            @PathVariable String code
    ) {
>>>>>>> origin/feature/voucher
        return ResponseEntity.ok(
            ApiResponse.success(voucherService.getVoucherByCode(code))
        );
    }
}
