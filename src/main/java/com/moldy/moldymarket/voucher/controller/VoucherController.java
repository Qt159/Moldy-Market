package com.moldy.moldymarket.voucher.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import com.moldy.moldymarket.voucher.dto.CreateVoucherRequest;
import com.moldy.moldymarket.voucher.dto.VoucherCheckResponse;
import com.moldy.moldymarket.voucher.dto.VoucherResponse;
import com.moldy.moldymarket.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Mã giảm giá (Vouchers)",
        description = "Kiểm tra tính hợp lệ và quản lý voucher khuyến mãi")
@RestController
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "Kiểm tra mã giảm giá",
            description = "Công khai: Kiểm tra voucher có hợp lệ, còn hạn và mức giảm giá")
    @GetMapping("/api/vouchers/{code}")
    public ResponseEntity<ApiResponse<VoucherCheckResponse>> checkVoucher(
            @PathVariable String code) {
        return ResponseEntity.ok(
                ApiResponse.success(voucherService.checkVoucher(code)));
    }

    @Operation(summary = "Tạo mã giảm giá mới",
            description = "Admin tạo voucher khuyến mãi mới với hạn dùng và mức giảm")
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

    @Operation(summary = "Danh sách tất cả voucher",
            description = "Admin xem danh sách toàn bộ mã giảm giá trong hệ thống")
    @IsAdmin
    @GetMapping("/api/admin/vouchers")
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getAllVouchers() {
        return ResponseEntity.ok(
                ApiResponse.success(voucherService.getAllVouchers()));
    }

    @Operation(summary = "Chi tiết voucher theo mã",
            description = "Admin xem chi tiết cấu hình của voucher theo mã code")
    @IsAdmin
    @GetMapping("/api/admin/vouchers/{code}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getVoucherByCode(
            @PathVariable String code) {
        return ResponseEntity.ok(
                ApiResponse.success(voucherService.getVoucherByCode(code)));
    }
}
