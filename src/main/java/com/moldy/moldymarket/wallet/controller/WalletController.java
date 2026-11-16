package com.moldy.moldymarket.wallet.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.wallet.dto.response.WalletResponse;
import com.moldy.moldymarket.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Ví điện tử (Wallets)",
        description = "Quản lý số dư và trạng thái ví của người dùng")
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WalletController {
    WalletService walletService;

    @Operation(summary = "Xem số dư ví người dùng",
            description = "Lấy thông tin ví và số dư khả dụng theo User UUID")
    @GetMapping("/{userId}/balance")
    public ResponseEntity<ApiResponse<WalletResponse>> getAvailableBalance(@PathVariable UUID userId) {
        WalletResponse walletResponse = walletService.getWalletByUserId(userId);

        return ResponseEntity.ok(ApiResponse.success(walletResponse));
    }
}
