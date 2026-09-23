package com.moldy.moldymarket.user.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.user.dto.*;
import com.moldy.moldymarket.user.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

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

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
            ) {
        AuthResponse response = authenticationService.login(request);

        return ResponseEntity.ok(
                ApiResponse.success("Login successful", response)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthResponse response = authenticationService.refresh(request.refreshToken());

        return ResponseEntity.ok(
                ApiResponse.success("Token refreshed successfully", response)
        );
    }

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
