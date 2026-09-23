package com.moldy.moldymarket.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // ==================== AUTH ====================

    INVALID_CREDENTIALS("AUTH_001", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_EXISTS("AUTH_002", "Email already exists", HttpStatus.CONFLICT),
    ACCOUNT_LOCKED("AUTH_003", "Account is locked", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED("AUTH_004", "Account is disabled", HttpStatus.FORBIDDEN),
    REFRESH_TOKEN_INVALID("AUTH_005", "Refresh token is invalid", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED("AUTH_006", "Refresh token has expired", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED("AUTH_007", "Refresh token reuse detected", HttpStatus.UNAUTHORIZED),

    // ==================== USER ====================

    USER_NOT_FOUND("USER_001", "User not found", HttpStatus.NOT_FOUND),

    // ==================== ROLE ====================

    ROLE_NOT_FOUND("ROLE_001", "Default role not found", HttpStatus.INTERNAL_SERVER_ERROR),

    // ==================== COMMON ====================

    VALIDATION_ERROR("COMMON_001", "Request validation failed", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("COMMON_002", "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("COMMON_003", "Authentication is required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("COMMON_004", "You do not have permission to perform this action", HttpStatus.FORBIDDEN),

    INTERNAL_SERVER_ERROR("COMMON_005", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(
            String code,
            String message,
            HttpStatus httpStatus
    ) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}