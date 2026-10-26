package com.moldy.moldymarket.user.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.user.dto.request.LoginRequest;
import com.moldy.moldymarket.user.dto.request.RefreshTokenRequest;
import com.moldy.moldymarket.user.dto.request.RegisterRequest;
import com.moldy.moldymarket.user.dto.response.AuthResponse;
import com.moldy.moldymarket.user.dto.response.RegisterResponse;
import com.moldy.moldymarket.user.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Xác thực (Auth)",
        description = "Đăng ký, đăng nhập, làm mới token và đăng xuất")
@RestController
@RequestMapping("/api/v1/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @Operation(summary = "Đăng ký tài khoản",
            description = "Tạo tài khoản người dùng mới trong hệ thống")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        RegisterResponse registerRespone = authenticationService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "Registration successfull",
                                registerRespone
                        )
                );
    }

    @Operation(summary = "Đăng nhập",
            description = "Xác thực tài khoản và trả về JWT access token + refresh token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response = authenticationService.login(request);

        return ResponseEntity.ok(
                ApiResponse.success("Login successful", response)
        );
    }

    @Operation(summary = "Làm mới access token",
            description = "Cấp access token mới bằng refresh token hợp lệ")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthResponse response = authenticationService.refresh(request.refreshToken());

        return ResponseEntity.ok(
                ApiResponse.success("Token refreshed successfully", response)
        );
    }

    @Operation(summary = "Đăng xuất",
            description = "Vô hiệu hóa refresh token của phiên đăng nhập hiện tại")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        authenticationService.logout(request.refreshToken());

        return ResponseEntity.ok(
                ApiResponse.success("Looged out successfully", null)
        );
    }
}
