package com.moldy.moldymarket.user.controller;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.security.jwt.JwtProperties;
import com.moldy.moldymarket.user.dto.request.LoginRequest;
import com.moldy.moldymarket.user.dto.request.RefreshTokenRequest;
import com.moldy.moldymarket.user.dto.response.AccessTokenResponse;
import com.moldy.moldymarket.user.dto.response.AuthResponse;
import com.moldy.moldymarket.user.dto.request.RegisterRequest;
import com.moldy.moldymarket.user.dto.response.RegisterResponse;
import com.moldy.moldymarket.user.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private static final String REFRESH_COOKIE_NAME = "moldy_refresh";
    private static final String AUTH_REQUEST_HEADER = "X-Moldy-Auth";

    private final JwtProperties jwtProperties;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpServletRequest
            ) {
        RegisterResponse registerRespone = authenticationService.register(request, httpServletRequest.getRemoteAddr());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                "Registration successfull",
                                registerRespone
                        )
                );
    }

    @PostMapping(value = "/login")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest,
            @RequestHeader(name = AUTH_REQUEST_HEADER, required = false) String authHeader
            ) {
        requireAuthHeader(authHeader);

        AuthResponse response = authenticationService.login(request, httpServletRequest.getRemoteAddr());

        return tokenResponse("Login successful", response);
    }

    @PostMapping(value = "/refresh")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(name = AUTH_REQUEST_HEADER, required = false) String authHeader
    ) {
        requireAuthHeader(authHeader);

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        AuthResponse response = authenticationService.refresh(refreshToken);
        return tokenResponse("Token refreshed successfully", response);
    }

    @PostMapping(value = "/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(name = AUTH_REQUEST_HEADER, required = false) String authHeader
    ) {
        requireAuthHeader(authHeader);

        if (refreshToken != null && !refreshToken.isBlank()) {
            authenticationService.logout(refreshToken);
        }

        ResponseCookie expiredCookie = refreshCookie("", Duration.ZERO);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(ApiResponse.success("Logged out successfully", null));
    }

    private ResponseCookie refreshCookie(String token, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }

    private ResponseEntity<ApiResponse<AccessTokenResponse>> tokenResponse(
            String message,
            AuthResponse response
    ) {
        AccessTokenResponse body = new AccessTokenResponse(
                response.userId(),
                response.email(),
                response.fullName(),
                response.accessToken(),
                response.tokenType()
        );

        ResponseCookie cookie = refreshCookie(
                response.refreshToken(),
                Duration.ofMillis(jwtProperties.refreshTokenExpiration())
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(message, body));
    }

    private void requireAuthHeader(String authHeader) {
        if (!"1".equals(authHeader)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "X-Moldy-Auth header must be 1");
        }
    }
}
