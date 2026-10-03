package com.moldy.moldymarket.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler lỗi tập trung cho toàn bộ ứng dụng.
 *
 * Thứ tự ưu tiên xử lý:
 * 1. AppException       — business errors có ErrorCode cụ thể
 * 2. Security errors    — auth/token
 * 3. Validation errors  — @Valid, body format
 * 4. Exception (catch-all) — fallback
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------------------------------------------------------------
    // 1. AppException — xử lý tất cả business errors
    // ---------------------------------------------------------------

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<?>> handleAppException(AppException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    // ---------------------------------------------------------------
    // 2. Security errors
    // ---------------------------------------------------------------

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ErrorCode code = ErrorCode.INVALID_CREDENTIALS;
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleInsufficientAuthentication(
            InsufficientAuthenticationException ex) {
        log.warn("Unauthorized access: {}", ex.getMessage());
        ErrorCode code = ErrorCode.UNAUTHENTICATED;
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }

    // ---------------------------------------------------------------
    // 3. Validation errors
    // ---------------------------------------------------------------

    /**
     * Lỗi @Valid — trả map { field: message } trong result.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        log.warn("Validation failed: {}", fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(
                        ErrorCode.INVALID_INPUT.name(),
                        ErrorCode.INVALID_INPUT.getMessage(),
                        fieldErrors
                ));
    }

    /**
     * Body gửi sai kiểu dữ liệu hoặc sai enum value.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        ErrorCode.INVALID_INPUT.name(),
                        "Dữ liệu gửi lên không đúng định dạng"
                ));
    }

    // ---------------------------------------------------------------
    // 4. Catch-all fallback
    // ---------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception ex) {
        log.error("Unexpected error: ", ex);
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.error(code.name(), code.getMessage()));
    }
}
