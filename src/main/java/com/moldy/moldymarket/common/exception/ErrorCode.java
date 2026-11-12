package com.moldy.moldymarket.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // AUTH

    INVALID_CREDENTIALS("AUTH_001", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_EXISTS("AUTH_002", "Email already exists", HttpStatus.CONFLICT),
    ACCOUNT_LOCKED("AUTH_003", "Account is locked", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED("AUTH_004", "Account is disabled", HttpStatus.FORBIDDEN),
    REFRESH_TOKEN_INVALID("AUTH_005", "Refresh token is invalid", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED("AUTH_006", "Refresh token has expired", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED("AUTH_007", "Refresh token reuse detected", HttpStatus.UNAUTHORIZED),

    // USER

    USER_NOT_FOUND("USER_001", "User not found", HttpStatus.NOT_FOUND),
    PHONE_ALREADY_EXISTS("USER_002", "Phone number already exists", HttpStatus.CONFLICT),
    SAME_PASSWORD("USER_003", "New password must be different from the current password", HttpStatus.BAD_REQUEST),

    // ROLE

    ROLE_NOT_FOUND("ROLE_001", "Default role not found", HttpStatus.INTERNAL_SERVER_ERROR),

    // STORE

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

    // VOUCHER

    VOUCHER_NOT_FOUND("VOUCHER_001", "Voucher not found", HttpStatus.NOT_FOUND),
    VOUCHER_CODE_ALREADY_EXISTS("VOUCHER_002", "Voucher code already exists", HttpStatus.CONFLICT),
    VOUCHER_EXPIRED("VOUCHER_003", "Voucher has expired", HttpStatus.UNPROCESSABLE_ENTITY),
    VOUCHER_OUT_OF_STOCK("VOUCHER_004", "Voucher is out of stock", HttpStatus.UNPROCESSABLE_ENTITY),

    // CATEGORY

    CATEGORY_NOT_FOUND("CATEGORY_001", "Category not found", HttpStatus.NOT_FOUND),
    CATEGORY_NAME_ALREADY_EXISTS("CATEGORY_002", "Category name already exists", HttpStatus.CONFLICT),
    CATEGORY_HAS_CHILDREN("CATEGORY_003", "Category has children", HttpStatus.CONFLICT),
    CATEGORY_HAS_LISTINGS("CATEGORY_004", "Category has listings", HttpStatus.CONFLICT),
    CATEGORY_INACTIVE("CATEGORY_005", "Category is inactive", HttpStatus.UNPROCESSABLE_ENTITY),
    CATEGORY_NOT_LEAF("CATEGORY_006", "Category is not a leaf", HttpStatus.UNPROCESSABLE_ENTITY),
    CATEGORY_MAX_DEPTH("CATEGORY_007", "Category max depth exceeded", HttpStatus.BAD_REQUEST),
    CATEGORY_SELF_PARENT("CATEGORY_008", "Category cannot be parent of itself", HttpStatus.BAD_REQUEST),
    SLUG_ALREADY_EXISTS("CATEGORY_009", "Slug already exists", HttpStatus.CONFLICT),

    // NOTIFICATION
    NOTIFICATION_NOT_FOUND("NOTIF_002", "Notification not found", HttpStatus.NOT_FOUND),
    NOTIFICATION_ALREADY_DELETED("NOTIF_003", "Notification already deleted", HttpStatus.CONFLICT),
    NOTIFICATION_NOT_DELETED("NOTIF_004", "Notification not deleted", HttpStatus.CONFLICT),
    PAGINATION_TOKEN_USER_MISMATCH("NOTIF_005", "Pagination token user mismatch", HttpStatus.UNAUTHORIZED),

    //  CHAT
    CONVERSATION_NOT_EXISTED(
            "CHAT_001", "Conversation does not exist",
            HttpStatus.NOT_FOUND),

    SENDER_NOT_IN_CONVERSATION(
            "CHAT_002", "Sender is not a member of this conversation",
            HttpStatus.FORBIDDEN),

    // WALLET

    WALLET_EXISTED(
            "WALLET_001", "Wallet already exists",
            HttpStatus.CONFLICT),

    WALLET_NOT_EXISTED(
            "WALLET_002", "Wallet does not exist",
            HttpStatus.NOT_FOUND),

    WALLET_INVALID_AMOUNT(
            "WALLET_003", "Wallet amount must be greater than zero",
            HttpStatus.BAD_REQUEST),

    INSUFFICIENT_BALANCE(
            "WALLET_004", "Insufficient wallet balance",
            HttpStatus.UNPROCESSABLE_ENTITY),

    // ESCROW

    ESCROW_NOT_FOUND(
            "ESCROW_001", "Escrow record not found",
            HttpStatus.NOT_FOUND),

    INVALID_ESCROW_STATE(
            "ESCROW_002", "Invalid escrow state for this operation",
            HttpStatus.CONFLICT),

    // WITHDRAWAL

    WITHDRAWAL_NOT_EXISTED(
            "WITHDRAWAL_001", "Withdrawal request does not exist",
            HttpStatus.NOT_FOUND),
    INVALID_WITHDRAWAL_STATE(
            "WITHDRAWAL_002", "Invalid withdrawal state for this operation",
            HttpStatus.CONFLICT),

    // PRICING & APPRAISER

    PRICING_MODEL_NOT_FOUND("PRICING_001", "Model does not exist in reference prices", HttpStatus.NOT_FOUND),
    PRICING_LISTING_NOT_FOUND("PRICING_002", "Listing not found", HttpStatus.NOT_FOUND),
    APPRAISER_NOT_FOUND_OR_INACTIVE("PRICING_003", "Appraiser not found or not active", HttpStatus.NOT_FOUND),
    APPRAISER_CATEGORY_NOT_MATCHED("PRICING_004", "Appraiser does not support this category", HttpStatus.UNPROCESSABLE_ENTITY),
    PRICING_REJECT_LIMIT_EXCEEDED("PRICING_005", "Seller has rejected 3 times, cannot request another appraisal for this listing", HttpStatus.UNPROCESSABLE_ENTITY),
    PRICING_USAGE_MONTHS_INVALID("PRICING_006", "Usage months cannot be negative", HttpStatus.UNPROCESSABLE_ENTITY),
    PRICING_REQUEST_ALREADY_PENDING("PRICING_007", "There is already a pending pricing request for this listing", HttpStatus.CONFLICT),
    APPRAISER_NOT_ASSIGNED("PRICING_008", "You are not the assigned appraiser for this request", HttpStatus.FORBIDDEN),
    PRICING_REQUEST_NOT_FOUND("PRICING_009", "Pricing request not found", HttpStatus.NOT_FOUND),
    APPRAISER_PROFILE_ALREADY_EXISTS("PRICING_010", "Appraiser profile already exists for this user", HttpStatus.CONFLICT),
    APPRAISER_CANNOT_APPRAISE_OWN_PRODUCT("PRICING_011", "Appraiser cannot appraise their own product", HttpStatus.FORBIDDEN),
    PRICING_REQUEST_ALREADY_CLAIMED("PRICING_012", "Pricing request has already been claimed by another appraiser", HttpStatus.CONFLICT),
    INVALID_PRICING_STATUS("PRICING_013", "Invalid pricing request status for this action", HttpStatus.CONFLICT),
    NOT_REQUEST_SELLER("PRICING_014", "Only the seller of this request can perform this action", HttpStatus.FORBIDDEN),
    APPRAISER_PROFILE_NOT_FOUND("PRICING_015", "Appraiser profile not found", HttpStatus.NOT_FOUND),

    // APPRAISAL MODULE
    APPRAISAL_REQUEST_NOT_FOUND("APPRAISAL_001", "Appraisal request not found", HttpStatus.NOT_FOUND),
    APPRAISAL_REQUEST_INVALID_STATUS("APPRAISAL_002", "Appraisal request has invalid status", HttpStatus.UNPROCESSABLE_ENTITY),
    APPRAISAL_REQUEST_EXPIRED("APPRAISAL_003", "Appraisal request has expired", HttpStatus.GONE),
    APPRAISAL_REQUEST_NOT_OWNER("APPRAISAL_004", "You are not the owner of this appraisal request", HttpStatus.FORBIDDEN),
    APPRAISAL_REQUEST_ALREADY_ASSIGNED("APPRAISAL_005", "Appraisal request is already assigned", HttpStatus.CONFLICT),
    
    APPRAISAL_APPLICATION_NOT_FOUND("APPRAISAL_006", "Appraisal application not found", HttpStatus.NOT_FOUND),
    APPRAISAL_APPLICATION_DUPLICATE("APPRAISAL_007", "You have already applied for this request", HttpStatus.CONFLICT),
    APPRAISAL_APPLICATION_INVALID_STATUS("APPRAISAL_008", "Appraisal application has invalid status", HttpStatus.UNPROCESSABLE_ENTITY),
    APPRAISAL_APPLICATION_NOT_OWNER("APPRAISAL_009", "You are not the owner of this application", HttpStatus.FORBIDDEN),
    APPRAISAL_APPLICATION_ALREADY_WITHDRAWN("APPRAISAL_010", "Application already withdrawn", HttpStatus.CONFLICT),
    
    APPRAISAL_ASSIGNMENT_NOT_FOUND("APPRAISAL_011", "Appraisal assignment not found", HttpStatus.NOT_FOUND),
    APPRAISAL_ASSIGNMENT_INVALID_STATUS("APPRAISAL_012", "Appraisal assignment has invalid status", HttpStatus.UNPROCESSABLE_ENTITY),
    APPRAISAL_ASSIGNMENT_NOT_OWNER("APPRAISAL_013", "You are not the owner of this assignment", HttpStatus.FORBIDDEN),
    APPRAISAL_ASSIGNMENT_ALREADY_ACTIVE("APPRAISAL_014", "Assignment is already active for this request", HttpStatus.CONFLICT),
    APPRAISAL_ASSIGNMENT_NOT_APPRAISER("APPRAISAL_015", "You are not the appraiser for this assignment", HttpStatus.FORBIDDEN),
    
    // PHASE 2: APPRAISER INVITATIONS
    APPRAISER_INVITATION_NOT_FOUND("APPRAISAL_016", "Appraiser invitation not found", HttpStatus.NOT_FOUND),
    APPRAISER_INVITATION_DUPLICATE("APPRAISAL_017", "You have already invited this appraiser for this request", HttpStatus.CONFLICT),
    APPRAISER_INVITATION_INVALID_STATUS("APPRAISAL_018", "Appraiser invitation has invalid status", HttpStatus.UNPROCESSABLE_ENTITY),
    APPRAISER_INVITATION_NOT_OWNER("APPRAISAL_019", "You are not the owner of this invitation", HttpStatus.FORBIDDEN),
    APPRAISER_INVITATION_EXPIRED("APPRAISAL_020", "This invitation has expired", HttpStatus.GONE),
    
    // PHASE 3: APPRAISAL REPORTS
    APPRAISAL_REPORT_NOT_FOUND("APPRAISAL_021", "Appraisal report not found", HttpStatus.NOT_FOUND),
    APPRAISAL_REPORT_ALREADY_SUBMITTED("APPRAISAL_022", "A report has already been submitted for this assignment", HttpStatus.CONFLICT),
    APPRAISAL_REPORT_INVALID_STATUS("APPRAISAL_023", "Appraisal report has invalid status", HttpStatus.UNPROCESSABLE_ENTITY),
    APPRAISAL_REPORT_NOT_OWNER("APPRAISAL_024", "You are not the owner of this report", HttpStatus.FORBIDDEN),
    APPRAISAL_REPORT_NOT_SUBMITTED("APPRAISAL_025", "Report has not been submitted yet", HttpStatus.UNPROCESSABLE_ENTITY),
    
    // PHASE 4: APPRAISER REVIEWS
    APPRAISER_REVIEW_NOT_FOUND("APPRAISAL_026", "Appraiser review not found", HttpStatus.NOT_FOUND),
    APPRAISER_REVIEW_ALREADY_EXISTS("APPRAISAL_027", "You have already reviewed this appraiser for this assignment", HttpStatus.CONFLICT),
    APPRAISER_REVIEW_NOT_OWNER("APPRAISAL_028", "You are not the owner of this review", HttpStatus.FORBIDDEN),
    APPRAISER_REVIEW_INVALID_RATING("APPRAISAL_029", "Rating must be between 1 and 5", HttpStatus.BAD_REQUEST),
    
    // PHASE 5: COMMISSIONS
    APPRAISER_COMMISSION_NOT_FOUND("APPRAISAL_030", "Appraiser commission not found", HttpStatus.NOT_FOUND),
    APPRAISER_COMMISSION_NOT_OWNER("APPRAISAL_031", "You are not the owner of this commission", HttpStatus.FORBIDDEN),
    APPRAISER_COMMISSION_INVALID_STATUS("APPRAISAL_032", "Commission has invalid status for this operation", HttpStatus.UNPROCESSABLE_ENTITY),
    
    //  COMMON

    VALIDATION_ERROR("COMMON_001", "Request validation failed", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("COMMON_002", "Invalid request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("COMMON_003", "Authentication is required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("COMMON_004", "You do not have permission to perform this action", HttpStatus.FORBIDDEN),
    INVALID_PAGINATION_TOKEN("COMMON_005", "Invalid pagination token", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("COMMON_006", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);



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