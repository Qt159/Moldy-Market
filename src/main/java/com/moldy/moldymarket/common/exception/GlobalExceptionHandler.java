package com.moldy.moldymarket.common.exception;

import com.moldy.moldymarket.common.ratelimit.RateLimitExceededException;
import com.moldy.moldymarket.common.response.ApiError;
import com.moldy.moldymarket.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleAppException(
            AppException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        ApiError error = ApiError.of(
                request.getRequestURI()
        );

        ApiResponse<ApiError> response = ApiResponse.failure(
                errorCode.name(),
                exception.getMessage(),
                error
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();

        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            validationErrors.put(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }

        ApiError error = ApiError.of(
                request.getRequestURI(),
                validationErrors
        );

        ApiResponse<ApiError> response = ApiResponse.failure(
                ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getMessage(),
                error
        );

        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        ApiError error = ApiError.of(request.getRequestURI());

        ApiResponse<ApiError> response = ApiResponse.failure(
                ErrorCode.FORBIDDEN.name(),
                ErrorCode.FORBIDDEN.getMessage(),
                error
        );

        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(response);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ApiError>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unexpected exception occurred. Path: {}",
                request.getRequestURI(),
                exception
        );

        ApiError error = ApiError.of(
                request.getRequestURI()
        );

        ApiResponse<ApiError> response = ApiResponse.failure(
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                error
        );

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(response);
    }

    // Body thiếu, JSON hỏng, sai kiểu dữ liệu, giá trị enum không tồn tại
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return build(ErrorCode.INVALID_REQUEST, "Request body is missing or malformed", request);
    }

    // Tham số query/path sai kiểu: ?status=abc, /products/khong-phai-uuid
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return build(ErrorCode.INVALID_REQUEST,
                "Invalid value for parameter '" + exception.getName() + "'", request);
    }

    // Thiếu tham số bắt buộc, ví dụ PATCH /status không có ?status=
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return build(ErrorCode.INVALID_REQUEST,
                "Missing required parameter '" + exception.getParameterName() + "'", request);
    }

    // ?sort=truongkhongton tai
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleUnknownProperty(
            PropertyReferenceException exception, HttpServletRequest request) {
        return build(ErrorCode.INVALID_REQUEST, "Unsupported sort or filter field", request);
    }

    // Sai URL
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleNoResource(
            NoResourceFoundException exception, HttpServletRequest request) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage(), request);
    }

    // Gọi sai method, ví dụ GET vào endpoint chỉ nhận POST
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage(), request);
    }

    // Quên header Content-Type: application/json
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage(), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleRateLimit(
            RateLimitExceededException exception, HttpServletRequest request) {
                ApiResponse<ApiError> response = ApiResponse.failure(
                        ErrorCode.TOO_MANY_REQUESTS.name(),
                        ErrorCode.TOO_MANY_REQUESTS.getMessage(),
                        ApiError.of(request.getRequestURI())
                );
                return ResponseEntity.status(ErrorCode.TOO_MANY_REQUESTS.getHttpStatus())
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                        .body(response);
    }

    // Hai người ghi đè cùng một bản ghi (Hibernate @Version)
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException exception, HttpServletRequest request) {
        return build(ErrorCode.CONCURRENT_MODIFICATION,
                ErrorCode.CONCURRENT_MODIFICATION.getMessage(), request);
    }

    // Vi phạm unique / khóa ngoại ở DB (thường do hai request chạy trùng lúc)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleDataIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Data integrity violation. Path: {}, cause: {}",
                request.getRequestURI(), exception.getMostSpecificCause().getMessage());
        return build(ErrorCode.DATA_CONFLICT, ErrorCode.DATA_CONFLICT.getMessage(), request);
    }

    private ResponseEntity<ApiResponse<ApiError>> build(
            ErrorCode errorCode, String message, HttpServletRequest request) {
        ApiResponse<ApiError> response = ApiResponse.failure(
                errorCode.name(),
                message,
                ApiError.of(request.getRequestURI())
        );
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }
}