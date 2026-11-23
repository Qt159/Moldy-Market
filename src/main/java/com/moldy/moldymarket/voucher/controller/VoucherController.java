package com.moldy.moldymarket.voucher.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import com.moldy.moldymarket.security.annotation.IsOwner;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.voucher.dto.CreateVoucherRequest;
import com.moldy.moldymarket.voucher.dto.VoucherCheckResponse;
import com.moldy.moldymarket.voucher.dto.VoucherResponse;
import com.moldy.moldymarket.voucher.dto.UpdateVoucherRequest;
import com.moldy.moldymarket.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Mã giảm giá (Vouchers)",
        description = "Kiểm tra tính hợp lệ và quản lý voucher khuyến mãi")
@RestController
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;
    private final CurrentUser currentUser;

    @Operation(summary = "Kiểm tra mã giảm giá",
            description = "Công khai: kiểm tra voucher đang hoạt động, còn hạn và còn số lượng. Checkout vẫn kiểm tra lại theo giỏ hàng và phạm vi cửa hàng.")
    @GetMapping("/api/vouchers/{code}")
    public ResponseEntity<ApiResponse<VoucherCheckResponse>> checkVoucher(
            @PathVariable String code) {
        return ResponseEntity.ok(
                ApiResponse.success(voucherService.checkVoucher(code)));
    }

    @Operation(summary = "Tạo mã giảm giá mới",
            description = "Admin creates a PLATFORM voucher. Issuer and store scope are assigned by the server.")
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

    @Operation(summary = "Create a store voucher", description = "Creates a STORE voucher for the authenticated shop owner's store.")
    @IsOwner
    @PostMapping("/api/shop/vouchers")
    public ResponseEntity<ApiResponse<VoucherResponse>> createStoreVoucher(
            @Valid @RequestBody CreateVoucherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Store voucher created successfully",
                voucherService.createStoreVoucher(request, currentUser.findCurrentUser().getId())));
    }

    @Operation(summary = "List my store vouchers", description = "Lists vouchers belonging to the authenticated owner's store.")
    @IsOwner
    @GetMapping("/api/shop/vouchers")
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getMyStoreVouchers() {
        return ResponseEntity.ok(ApiResponse.success(
                voucherService.getMyStoreVouchers(currentUser.findCurrentUser().getId())));
    }

    @Operation(summary = "Get my store voucher")
    @IsOwner
    @GetMapping("/api/shop/vouchers/{code}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getMyStoreVoucher(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherService.getMyStoreVoucher(code, currentUser.findCurrentUser().getId())));
    }

    @Operation(summary = "Update my store voucher", description = "Only discount and quantity settings change; issuer and store ownership are immutable.")
    @IsOwner
    @PutMapping("/api/shop/vouchers/{code}")
    public ResponseEntity<ApiResponse<VoucherResponse>> updateMyStoreVoucher(
            @PathVariable String code, @Valid @RequestBody UpdateVoucherRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Store voucher updated successfully",
                voucherService.updateMyStoreVoucher(code, currentUser.findCurrentUser().getId(), request)));
    }

    @Operation(summary = "Deactivate my store voucher", description = "Soft deactivates the voucher; existing order snapshots are unchanged.")
    @IsOwner
    @DeleteMapping("/api/shop/vouchers/{code}")
    public ResponseEntity<Void> deactivateMyStoreVoucher(@PathVariable String code) {
        voucherService.deactivateMyStoreVoucher(code, currentUser.findCurrentUser().getId());
        return ResponseEntity.noContent().build();
    }
}
