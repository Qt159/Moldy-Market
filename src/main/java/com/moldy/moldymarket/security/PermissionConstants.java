package com.moldy.moldymarket.security;

public final class PermissionConstants {

    private PermissionConstants() {}

    // --M2: Profile & Workspace ----------------------
    public static final String PROFILE_MANAGE = "PROFILE_MANAGE";
    public static final String BANK_ACCOUNT_MANAGE = "BANK_ACCOUNT_MANAGE";
    public static final String WORKSPACE_SWITCH = "WORKSPACE_SWITCH";

    // --M3: Product --------------------
    public static final String PRODUCT_CREATE = "PRODUCT_CREATE";
    public static final String PRODUCT_MANAGE_OWN = "PRODUCT_MANAGE_OWN";
    public static final String APPRAISAL_REQUEST_CREATE = "APPRAISAL_REQUEST_CREATE";
    public static final String VOUCHER_SELLER_MANAGE = "VOUCHER_SELLER_MANAGE";

    // --M4: Appraisal --------------------
    public static final String APPRAISER_APPLY = "APPRAISER_APPLY";
    public static final String APPRAISAL_QUEUE_VIEW = "APPRAISAL_QUEUE_VIEW";
    public static final String APPRAISAL_TASK_ACCEPT = "APPRAISAL_TASK_ACCEPT";
    public static final String APPRAISAL_SUBMIT = "APPRAISAL_SUBMIT";

    // --M5: Offer -----------------------
    public static final String OFFER_CREATE = "OFFER_CREATE";
    public static final String OFFER_HANDLE = "OFFER_HANDLE";
    public static final String OFFER_OVERRIDE_FLOOR = "OFFER_OVERRIDE_FLOOR";

    // --M6: Order, Return, Dispute ------------------
    public static final String ORDER_PAY = "ORDER_PAY";
    public static final String ORDER_TRACK = "ORDER_TRACK";
    public static final String ORDER_CONFIRM_RECEIVED = "ORDER_CONFIRM_RECEIVED";
    public static final String ORDER_PROCESS = "ORDER_PROCESS";
    public static final String ORDER_CANCEL = "ORDER_CANCEL";
    public static final String ORDER_RETURN_REQUEST = "ORDER_RETURN_REQUEST";
    public static final String ORDER_RETURN_HANDLE = "ORDER_RETURN_HANDLE";
    public static final String DISPUTE_CREATE = "DISPUTE_CREATE";
    public static final String DISPUTE_RESPOND = "DISPUTE_RESPOND";
    public static final String REVIEW_CREATE = "REVIEW_CREATE";
    public static final String CHAT_SEND = "CHAT_SEND";

    // --M7: Wallet & Finance ------------------
    public static final String WALLET_VIEW = "WALLET_VIEW";
    public static final String WALLET_WITHDRAW = "WALLET_WITHDRAW";
    public static final String STORE_STATS_VIEW = "STORE_STATS_VIEW";

    // --M8: Store -----------------
    public static final String STORE_REGISTER = "STORE_REGISTER";
    public static final String STORE_STAFF_MANAGE = "STORE_STAFF_MANAGE";
    public static final String STORE_WORKSPACE_ACCESS = "STORE_WORKSPACE_ACCESS";

    // --M9: Admin ------------------
    public static final String ADMIN_PORTAL_ACCESS = "ADMIN_PORTAL_ACCESS";
    public static final String CATEGORY_MANAGE = "CATEGORY_MANAGE";
    public static final String USER_STORE_MANAGE = "USER_STORE_MANAGE";
    public static final String APPRAISER_APPROVE = "APPRAISER_APPROVE";
    public static final String VOUCHER_SYSTEM_MANAGE = "VOUCHER_SYSTEM_MANAGE";
    public static final String DISPUTE_ADJUDICATE = "DISPUTE_ADJUDICATE";
    public static final String LEGIT_POINTS_MANAGE = "LEGIT_POINTS_MANAGE";
    public static final String ANALYTICS_SYSTEM_VIEW = "ANALYTICS_SYSTEM_VIEW";
}
