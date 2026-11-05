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

    // ==================== STORE ====================

    STORE_NOT_FOUND("STORE_001", "Store not found", HttpStatus.NOT_FOUND),
    STORE_ALREADY_EXISTS("STORE_002", "You already have a store", HttpStatus.CONFLICT),
    STORE_NAME_TAKEN("STORE_003", "Store name is already taken", HttpStatus.CONFLICT),
    STORE_BLOCKED("STORE_004", "Your store has been blocked", HttpStatus.FORBIDDEN),
    STAFF_NOT_FOUND("STORE_005", "Staff not found in this store", HttpStatus.NOT_FOUND),
    STAFF_ALREADY_EXISTS("STORE_006", "User is already a staff of this store", HttpStatus.CONFLICT),
    CANNOT_ADD_SELF_AS_STAFF("STORE_007", "Store owner cannot be added as a staff", HttpStatus.BAD_REQUEST),
    INVALID_STAFF_PERMISSION("STORE_008", "One or more permissions are invalid for staff", HttpStatus.BAD_REQUEST),
    CANNOT_ADD_STORE_OWNER_AS_STAFF("STORE_009", "A store owner cannot be added as a staff", HttpStatus.BAD_REQUEST),
    STAFF_ACCOUNT_NOT_ACTIVE("STORE_010", "Staff account must be active and verified", HttpStatus.BAD_REQUEST),
    STAFF_ALREADY_EMPLOYED("STORE_011", "User is already employed at another store", HttpStatus.CONFLICT),
    CANNOT_ADD_ADMIN_AS_STAFF("STORE_012", "Admin account cannot be added as a store staff", HttpStatus.BAD_REQUEST),
    CANNOT_ADD_APPRAISER_AS_STAFF("STORE_013", "Appraiser account cannot be added as a store staff", HttpStatus.BAD_REQUEST),
    STORE_STAFF_LIMIT_REACHED("STORE_014", "Store has reached the maximum staff limit (10 staff)", HttpStatus.BAD_REQUEST),
    STORE_ACCESS_DENIED("STORE_015", "You do not have access to manage this store's resources", HttpStatus.FORBIDDEN),

    // ==================== CATEGORY ====================

    CATEGORY_NOT_FOUND("CAT_001", "Category not found", HttpStatus.NOT_FOUND),
    CATEGORY_HAS_CHILDREN("CAT_002", "Cannot delete: category has sub-categories", HttpStatus.CONFLICT),
    CATEGORY_HAS_LISTINGS("CAT_003", "Cannot delete: category has products", HttpStatus.CONFLICT),
    CATEGORY_INACTIVE("CAT_004", "Category is not active", HttpStatus.BAD_REQUEST),
    CATEGORY_NOT_LEAF("CAT_005", "Products must be assigned to a sub-category", HttpStatus.BAD_REQUEST),
    CATEGORY_PARENT_NOT_ROOT("CAT_006", "Parent category must be a root category", HttpStatus.BAD_REQUEST),
    CATEGORY_NAME_ALREADY_EXISTS("CAT_007", "Category name already exists under the same parent", HttpStatus.CONFLICT),

    // ==================== PRODUCT ====================
    PRODUCT_NOT_FOUND("PROD_001", "Product not found", HttpStatus.NOT_FOUND),
    PRODUCT_ACCESS_DENIED("PROD_002", "You do not have access to this product", HttpStatus.FORBIDDEN),
    PRICING_STRATEGY_NOT_SUPPORTED("PROD_003", "This pricing strategy is not supported yet", HttpStatus.BAD_REQUEST),
    PRODUCT_STATUS_NOT_ALLOWED("PROD_004", "Seller can only switch a product between ACTIVE and HIDDEN", HttpStatus.BAD_REQUEST),
    PRODUCT_STATUS_LOCKED("PROD_005", "The status of this product can no longer be changed", HttpStatus.CONFLICT),
    PRODUCT_REMOVED("PROD_006", "This product has been removed by an administrator", HttpStatus.FORBIDDEN),
    PRODUCT_NOT_REMOVED("PROD_007", "This product is not in removed state", HttpStatus.CONFLICT),
    PRODUCT_LIMIT_REACHED("PROD_008", "Listing limit reached", HttpStatus.CONFLICT),
    PRODUCT_ALREADY_REMOVED("PROD_009", "Product has already been removed", HttpStatus.CONFLICT),
    PRODUCT_NOT_ACTIVE("PROD_010", "Product is not active", HttpStatus.BAD_REQUEST),
    INVALID_PRODUCT("PROD_011", "Product is invalid", HttpStatus.BAD_REQUEST),


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

    INTERNAL_SERVER_ERROR("COMMON_005", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),


    // ==================== WALLET ====================
    WALLET_EXISTED("WALLET_001", "Wallet is existed", HttpStatus.BAD_REQUEST),
    WALLET_NOT_EXISTED("WALLET_002", "Wallet is not existed", HttpStatus.NOT_FOUND),
    WALLET_INVALID_AMOUNT("WALLET_003", "The amount must be larger than 0", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_BALANCE("WALLET_004", "The available balance is insufficient to withdraw", HttpStatus.BAD_REQUEST),
    WITHDRAWAL_NOT_EXISTED("WALLET_005", "Withdrawal request is not found", HttpStatus.NOT_FOUND),
    INVALID_WITHDRAWAL_STATE("WALLET_006", "Withdrawal is invalid state", HttpStatus.BAD_REQUEST),
    ESCROW_NOT_FOUND("WALLET_007", "Escrow is not found", HttpStatus.NOT_FOUND),
    INVALID_ESCROW_STATE("WALLET_008", "Withdrawal is invalid state", HttpStatus.BAD_REQUEST),

    // ==================== CHAT ====================
    CONVERSATION_NOT_EXISTED("CHAT_001", "Conversation is not existed", HttpStatus.NOT_FOUND),
    SENDER_NOT_IN_CONVERSATION("CHAT_002", "Sender is not in this conversation", HttpStatus.BAD_REQUEST),
    CHAT_INVALID_PARTICIPANTS("CHAT_003", "Invalid participants", HttpStatus.BAD_REQUEST),
    CHAT_NOT_PARTICIPANT("CHAT_004", "You are not participant in this chat", HttpStatus.BAD_REQUEST),

    // ==================== PAYMENT ====================
    PAYMENT_NOT_EXISTED("PAYMENT_001", "Payment is not existed", HttpStatus.NOT_FOUND),
    WEBHOOK_UNAUTHORIZED("PAYMENT_002", "Webhook is unauthorized", HttpStatus.UNAUTHORIZED),
    WEBHOOK_INVALID_REQUEST("PAYMENT_003", "Webhook request is invalid", HttpStatus.BAD_REQUEST),

    // ==================== ORDER ====================
    ORDER_NOT_EXISTED("ORDER_001", "Order is not existed", HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATE("ORDER_002", "Invalid order state", HttpStatus.BAD_REQUEST),
    ORDER_LIST_NOT_FOUND("ORDER_003", "Order list is not found",HttpStatus.NOT_FOUND),
    ORDER_ITEMS_LIST_NOT_EXISTED("ORDER_004", "Order items list is null", HttpStatus.BAD_REQUEST),
    DUPLICATE_PRODUCT("ORDER_005", "Duplicated product", HttpStatus.BAD_REQUEST),
    INVALID_ORDER_AMOUNT("ORDER_006", "Order amount is invalid", HttpStatus.BAD_REQUEST),
    INVALID_ORDER_ITEMS("ORDER_007", "Order item is invalid", HttpStatus.BAD_REQUEST),
    CHECKOUT_GROUP_INVALID_STATUS("ORDER_008", "Checkout group is not in a valid state for this operation", HttpStatus.CONFLICT),

    RESOURCE_NOT_FOUND("COMMON_006", "The requested resource was not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("COMMON_007", "HTTP method is not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("COMMON_008", "Content type is not supported", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    TOO_MANY_REQUESTS("COMMON_009", "Too many requests, please try again later", HttpStatus.TOO_MANY_REQUESTS),
    CONCURRENT_MODIFICATION("COMMON_010", "The resource was modified by someone else, please reload and try again", HttpStatus.CONFLICT),
    DATA_CONFLICT("COMMON_011", "Request conflicts with existing data", HttpStatus.CONFLICT),
    ;


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