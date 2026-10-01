package com.moldy.moldymarket.wallet.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.wallet.dto.response.WalletResponse;
import com.moldy.moldymarket.wallet.service.WalletService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WalletController {
    WalletService walletService;

    @GetMapping("/{userId}/balance")
    ResponseEntity<ApiResponse<WalletResponse>> getAvailableBalance(@PathVariable UUID userId){
        WalletResponse walletResponse = walletService.getWalletByUserId(userId);

        return ResponseEntity.ok(ApiResponse.success(walletResponse));
    }


}
