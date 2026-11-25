package com.moldy.moldymarket.user.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.user.dto.request.ForgotPasswordRequest;
import com.moldy.moldymarket.user.dto.request.ResetPasswordRequest;
import com.moldy.moldymarket.user.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest
            ) {
        passwordResetService.requestReset(request.email(), servletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest
            ) {
        passwordResetService.resetPassword(
                request.email(),
                request.code(),
                request.newPassword(),
                servletRequest.getRemoteAddr()
        );
        return ResponseEntity.ok(ApiResponse.<Void>success(null));
    }
}
