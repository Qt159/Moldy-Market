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
    PHONE_ALREADY_EXISTS("USER_002", "Phone number already exists", HttpStatus.CONFLICT),
    SAME_PASSWORD("USER_003", "New password must be different from the current password", HttpStatus.BAD_REQUEST),

    // ==================== ROLE ====================

    ROLE_NOT_FOUND("ROLE_001", "Default role not found", HttpStatus.INTERNAL_SERVER_ERROR),

    // ==================== CATEGORY ====================

    CATEGORY_NOT_FOUND("CAT_001", "Category not found", HttpStatus.NOT_FOUND),
    CATEGORY_HAS_CHILDREN("CAT_002", "Cannot delete: category has sub-categories", HttpStatus.CONFLICT),
    CATEGORY_HAS_LISTINGS("CAT_003", "Cannot delete: category has associated listings", HttpStatus.CONFLICT),
    CATEGORY_INACTIVE("CAT_004", "Category is not active", HttpStatus.UNPROCESSABLE_ENTITY),
    CATEGORY_NOT_LEAF("CAT_005", "Products must be assigned to a sub-category", HttpStatus.UNPROCESSABLE_ENTITY),
    CATEGORY_MAX_DEPTH("CAT_006", "Parent category must be a root category", HttpStatus.BAD_REQUEST),
    CATEGORY_NAME_ALREADY_EXISTS("CAT_007", "Category name already exists under the same parent", HttpStatus.CONFLICT),

    // ==================== NOTIFICATION ====================

    NOTIFICATION_NOT_FOUND("NOTIFICATION_001", "Notification not found", HttpStatus.NOT_FOUND),
    NOTIFICATION_ALREADY_DELETED("NOTIFICATION_002", "Notification has already been deleted", HttpStatus.CONFLICT),
    NOTIFICATION_NOT_DELETED("NOTIFICATION_003", "Notification has not been deleted", HttpStatus.CONFLICT),
    INVALID_PAGINATION_TOKEN("NOTIFICATION_004", "Invalid pagination token", HttpStatus.BAD_REQUEST),
    PAGINATION_TOKEN_USER_MISMATCH("NOTIFICATION_005", "Pagination token does not belong to current user", HttpStatus.BAD_REQUEST),

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