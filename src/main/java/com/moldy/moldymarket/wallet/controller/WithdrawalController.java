package com.moldy.moldymarket.wallet.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.PermissionConstants;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Rút tiền (Withdrawals)",
        description = "Yêu cầu và duyệt quy trình rút tiền về tài khoản ngân hàng")
@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WithdrawalController {
    WithdrawalService withdrawalService;

    @Operation(summary = "Danh sách yêu cầu rút tiền của tôi",
            description = "Lấy lịch sử các yêu cầu rút tiền của tài khoản đang đăng nhập")
    @PreAuthorize("hasAuthority('" + PermissionConstants.WALLET_VIEW + "')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<WithdrawalResponse>>> getMyWithdrawals() {
        return ResponseEntity.ok(ApiResponse.success(withdrawalService.getMyWithdrawals()));
    }

    @Operation(summary = "Tạo yêu cầu rút tiền",
            description = "Người dùng yêu cầu rút số dư ví về tài khoản ngân hàng")
    @PreAuthorize("hasAuthority('" + PermissionConstants.WALLET_WITHDRAW + "')")
    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalResponse>> requestWithdrawal(@Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Withdrawal request is created", withdrawalService.requestWithdrawal(request)));
    }

    @Operation(summary = "Hủy yêu cầu rút tiền đang chờ",
            description = "Chủ ví hủy yêu cầu rút tiền ở trạng thái REQUESTED và hoàn lại số dư khả dụng")
    @PreAuthorize("hasAuthority('" + PermissionConstants.WALLET_WITHDRAW + "')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> cancelMyWithdrawal(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is cancelled", withdrawalService.cancelMyWithdrawal(id)));
    }

    @Operation(summary = "Danh sách yêu cầu rút tiền (Admin)",
            description = "Admin xem toàn bộ yêu cầu rút tiền của người dùng")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ADMIN_PORTAL_ACCESS + "')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<WithdrawalResponse>>> getAllForAdmin() {
        return ResponseEntity.ok(ApiResponse.success(withdrawalService.getAllWithdrawalsForAdmin()));
    }

    @Operation(summary = "Duyệt yêu cầu rút tiền",
            description = "Admin phê duyệt yêu cầu rút tiền")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ADMIN_PORTAL_ACCESS + "')")
    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is approved", withdrawalService.approve(id)));
    }

    @Operation(summary = "Hoàn tất rút tiền",
            description = "Admin xác nhận đã chuyển khoản thành công cho người dùng")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ADMIN_PORTAL_ACCESS + "')")
    @PatchMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> complete(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is completed", withdrawalService.complete(id)));
    }

    @Operation(summary = "Từ chối rút tiền",
            description = "Admin từ chối yêu cầu rút tiền kèm lý do")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ADMIN_PORTAL_ACCESS + "')")
    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> reject(@PathVariable Long id, @Valid @RequestBody RejectWithdrawal rejectWithdrawal) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is rejected", withdrawalService.reject(id, rejectWithdrawal.reason())));
    }

    @Operation(summary = "Đánh dấu rút tiền thất bại",
            description = "Admin ghi nhận giao dịch chuyển khoản thất bại")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ADMIN_PORTAL_ACCESS + "')")
    @PatchMapping("/{id}/fail")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> fail(@PathVariable Long id, @Valid @RequestBody RejectWithdrawal rejectWithdrawal) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal request is failed", withdrawalService.fail(id, rejectWithdrawal.reason())));
    }
}
