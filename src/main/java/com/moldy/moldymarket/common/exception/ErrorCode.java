package com.moldy.moldymarket.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    PRODUCT_NOT_AVAILABLE("PROD_012", "Product is not available", HttpStatus.BAD_REQUEST),
    WALLET_INSUFFICIENT_HELD_BALANCE("WALLET_009", "Held balance in wallet is insufficient", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_METHOD("PAYMENT_004", "Payment method is invalid", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_STATE("PAYMENT_005", "Payment status is invalid", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_AMOUNT("PAYMENT_006", "Payment amount is invalid", HttpStatus.BAD_REQUEST),
    CANNOT_BUY_OWN_PRODUCT("ORDER_012", "You must not buy this product", HttpStatus.FORBIDDEN),
    INVALID_ORDER("ORDER_013", "Order is invalid", HttpStatus.BAD_REQUEST),
    DISPUTE_NOT_EXISTED("DISPUTE_001", "Dispute is not existed", HttpStatus.NOT_FOUND),
    DISPUTE_NOT_ELIGIBLE("DISPUTE_002", "This order cannot be disputed", HttpStatus.CONFLICT),
    DISPUTE_PERIOD_EXPIRED("DISPUTE_003", "The dispute period has expired", HttpStatus.CONFLICT),
    DISPUTE_ALREADY_OPEN("DISPUTE_004", "A dispute is already open for this order", HttpStatus.CONFLICT),
    DISPUTE_INVALID_STATE("DISPUTE_005", "The dispute is no longer open", HttpStatus.CONFLICT),
    DISPUTE_EXISTED("DISPUTE_006", "Dispute request is existed", HttpStatus.CONFLICT),
    INVALID_REFUND_STATE("REFUND_001", "Refund state is invalid", HttpStatus.BAD_REQUEST),
    REFUND_NOT_EXISTED("REFUND_002", "Refund is not existed", HttpStatus.NOT_FOUND),
    REFUND_NOT_IMPLEMENTED("REFUND_001", "Refund processing is not available yet", HttpStatus.NOT_IMPLEMENTED),


    // ==================== AUTH ====================

    INVALID_CREDENTIALS("AUTH_001", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_EXISTS("AUTH_002", "Email already exists", HttpStatus.CONFLICT),
    ACCOUNT_LOCKED("AUTH_003", "Account is locked", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED("AUTH_004", "Account is disabled", HttpStatus.FORBIDDEN),
    REFRESH_TOKEN_INVALID("AUTH_005", "Refresh token is invalid", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED("AUTH_006", "Refresh token has expired", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED("AUTH_007", "Refresh token reuse detected", HttpStatus.UNAUTHORIZED),
    EMAIL_NOT_VERIFIED("AUTH_008", "Email has not been verified", HttpStatus.FORBIDDEN),
    OTP_INVALID("AUTH_009", "Verification code is invalid", HttpStatus.BAD_REQUEST),
    OTP_EXPIRED("AUTH_010", "Verification code has expired or was not requested", HttpStatus.BAD_REQUEST),
    OTP_ATTEMPTS_EXCEEDED("AUTH_011", "Too many incorrect attempts, please request a new code", HttpStatus.TOO_MANY_REQUESTS),
    OTP_RESEND_TOO_SOON("AUTH_012", "Please wait before requesting another verification code", HttpStatus.TOO_MANY_REQUESTS),
    EMAIL_DELIVERY_FAILED("AUTH_013", "Unable to send verification email", HttpStatus.SERVICE_UNAVAILABLE),
    // ==================== USER ====================

    USER_NOT_FOUND("USER_001", "User not found", HttpStatus.NOT_FOUND),
    PHONE_ALREADY_EXISTS("USER_002", "Phone number already exists", HttpStatus.CONFLICT),
    SAME_PASSWORD("USER_003", "New password must be different from the current password", HttpStatus.BAD_REQUEST),
    CANNOT_LOCK_YOURSELF("USER_004", "You cannot lock yourself", HttpStatus.FORBIDDEN),
    USER_CANNOT_LOCK_ADMIN("USER_005", "User cannot lock admin", HttpStatus.FORBIDDEN),
    USER_ALREADY_LOCKED("USER_006", "User is already locked", HttpStatus.CONFLICT),
    USER_PENDING_VERIFICATION("USER_007", "User is pending verification", HttpStatus.CONFLICT),
    USER_NOT_LOCKED("USER_008", "User is not locked", HttpStatus.CONFLICT),
    USER_STATUS_TRANSITION_INVALID("USER_009", "User transition is invalid", HttpStatus.BAD_REQUEST),

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
    PRODUCT_RESERVED("PROD_012", "Product is reserved by another checkout", HttpStatus.CONFLICT),


    // ==================== NOTIFICATION ====================

    NOTIFICATION_NOT_FOUND("NOTIFICATION_001", "Notification not found", HttpStatus.NOT_FOUND),
    NOTIFICATION_ALREADY_DELETED("NOTIFICATION_002", "Notification has already been deleted", HttpStatus.CONFLICT),
    NOTIFICATION_NOT_DELETED("NOTIFICATION_003", "Notification has not been deleted", HttpStatus.CONFLICT),
    INVALID_PAGINATION_TOKEN("NOTIFICATION_004", "Invalid pagination token", HttpStatus.BAD_REQUEST),
    PAGINATION_TOKEN_USER_MISMATCH("NOTIFICATION_005", "Pagination token does not belong to current user", HttpStatus.BAD_REQUEST),

    // ==================== VOUCHER ====================

    VOUCHER_NOT_FOUND("VOUCHER_001", "Voucher not found", HttpStatus.NOT_FOUND),
    VOUCHER_CODE_ALREADY_EXISTS("VOUCHER_002", "Voucher code already exists", HttpStatus.CONFLICT),
    VOUCHER_EXPIRED("VOUCHER_003", "Voucher has expired", HttpStatus.UNPROCESSABLE_ENTITY),
    VOUCHER_NOT_APPLICABLE("VOUCHER_005", "Voucher is not applicable", HttpStatus.UNPROCESSABLE_ENTITY),
    VOUCHER_MINIMUM_ORDER_NOT_MET("VOUCHER_006", "Minimum order value is not met", HttpStatus.UNPROCESSABLE_ENTITY),
    VOUCHER_STORE_MISMATCH("VOUCHER_007", "Voucher belongs to another store", HttpStatus.UNPROCESSABLE_ENTITY),
    VOUCHER_OUT_OF_STOCK("VOUCHER_004", "Voucher is out of stock", HttpStatus.UNPROCESSABLE_ENTITY),

    // ==================== CATEGORY ====================

    CATEGORY_MAX_DEPTH("CATEGORY_007", "Category max depth exceeded", HttpStatus.BAD_REQUEST),
    CATEGORY_SELF_PARENT("CATEGORY_008", "Category cannot be parent of itself", HttpStatus.BAD_REQUEST),
    SLUG_ALREADY_EXISTS("CATEGORY_009", "Slug already exists", HttpStatus.CONFLICT),

    CART_OWN_PRODUCT("CART_001", "You cannot add products from your own or linked store to your cart", HttpStatus.FORBIDDEN),

    // ==================== COMMON ====================

    VALIDATION_ERROR("COMMON_001", "Request validation failed", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("COMMON_002", "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("COMMON_003", "Authentication is required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("COMMON_004", "You do not have permission to perform this action", HttpStatus.FORBIDDEN),

    INTERNAL_SERVER_ERROR("COMMON_005", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_NOT_UPLOADED("FILE_001", "File has not been uploaded", HttpStatus.BAD_REQUEST),
    FILE_NOT_AVAILABLE("FILE_002", "File is not available", HttpStatus.BAD_REQUEST),
    FILE_STORAGE_UNAVAILABLE("FILE_003", "File storage is temporarily unavailable", HttpStatus.BAD_GATEWAY),


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
    CHAT_STORE_MEMBER("CHAT_005", "Store members cannot use customer chat with their own store", HttpStatus.FORBIDDEN),
    CHAT_PARTICIPANT_UNAVAILABLE("CHAT_006", "A chat participant is unavailable", HttpStatus.FORBIDDEN),

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
    ORDER_NOT_DELIVERED("ORDER_009", "Order has not been delivered", HttpStatus.BAD_REQUEST),
    CHECKOUT_GROUP_NOT_EXISTED("ORDER_010", "Checkout group is not existed", HttpStatus.NOT_FOUND),
    NOT_ALLOWED_ACCESS_CHECKOUT_GROUP("ORDER_011", "You aren't allowed to access this checkout group", HttpStatus.FORBIDDEN),
    CHECKOUT_OWN_PRODUCT("ORDER_012", "You cannot purchase your own products or products from a store you own or work for", HttpStatus.FORBIDDEN),

    // ==================== OFFER ====================
    OFFER_NOT_ALLOWED("OFF_001", "There must not be allowed offer", HttpStatus.BAD_REQUEST),
    CANNOT_MAKE_OFFER_FIRST("OFF_002", "Sellers can not make offer first", HttpStatus.BAD_REQUEST),
    INVALID_OFFER_PRICE("OFF_003", "This offer price is invalid", HttpStatus.BAD_REQUEST),
    OFFER_LIMIT_REACHED("OFF_004", "This offer reached limit", HttpStatus.BAD_REQUEST),
    OFFER_ALREADY_EXISTED("OFF_005", "This offer is already existed", HttpStatus.BAD_REQUEST),
    OFFER_NOT_EXISTED("OFF_006", "This offer is not existed", HttpStatus.NOT_FOUND),
    OFFER_INVALID_STATE("OFF_007", "Offer is invalid state", HttpStatus.BAD_REQUEST),
    OFFER_EXPIRED("OFF_008", "Offer is expired", HttpStatus.BAD_REQUEST),
    OFFER_NOT_YOUR_TURN("OFF_009", "Offer is not your turn", HttpStatus.BAD_REQUEST),
    OFFER_VOUCHER_NOT_ALLOWED("OFF_010", "Vouchers cannot be combined with an accepted offer", HttpStatus.BAD_REQUEST),

    RESOURCE_NOT_FOUND("COMMON_006", "The requested resource was not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("COMMON_007", "HTTP method is not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("COMMON_008", "Content type is not supported", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    TOO_MANY_REQUESTS("COMMON_009", "Too many requests, please try again later", HttpStatus.TOO_MANY_REQUESTS),
    CONCURRENT_MODIFICATION("COMMON_010", "The resource was modified by someone else, please reload and try again", HttpStatus.CONFLICT),
    DATA_CONFLICT("COMMON_011", "Request conflicts with existing data", HttpStatus.CONFLICT),

    // ==================== REVIEW ====================
    REVIEW_ALREADY_EXISTS("REVIEW_001", "Order has already been reviewed", HttpStatus.CONFLICT),
    INVALID_PAGE("COMMON_012", "Page is invalid", HttpStatus.BAD_REQUEST),
    INVALID_PAGE_SIZE("COMMON_013", "Page size is invalid", HttpStatus.BAD_REQUEST),

    // ==================== APPRAISER ====================
    APPRAISER_NOT_FOUND("APPRAISER_001", "Appraiser not found", HttpStatus.NOT_FOUND),
    APPRAISER_USER_NOT_FOUND("APPRAISER_002", "User for appraiser not found", HttpStatus.NOT_FOUND),
    APPRAISER_ALREADY_REGISTERED("APPRAISER_003", "User is already registered as an appraiser", HttpStatus.CONFLICT),
    APPRAISER_PROFILE_INACTIVE("APPRAISER_004", "Appraiser profile is inactive", HttpStatus.BAD_REQUEST),
    APPRAISER_PROFILE_NOT_PUBLICLY_AVAILABLE("APPRAISER_005", "Appraiser profile is not publicly available", HttpStatus.NOT_FOUND),
    APPRAISER_CATEGORY_NOT_FOUND("APPRAISER_006", "One or more categories not found", HttpStatus.NOT_FOUND),
    APPRAISER_STATUS_INVALID_FOR_UPDATE("APPRAISER_007", "Appraiser status is invalid for this operation", HttpStatus.BAD_REQUEST),
    APPRAISER_PROFILE_NOT_FOUND("APPRAISER_008", "Appraiser profile not found", HttpStatus.NOT_FOUND),

    // ==================== WISHLIST ====================
    WISHLIST_ITEM_NOT_FOUND("WISHLIST_001", "Wishlist item not found", HttpStatus.NOT_FOUND),
    PRODUCT_ALREADY_WISHLISTED("WISHLIST_002", "Product is already in your wishlist", HttpStatus.CONFLICT),
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
