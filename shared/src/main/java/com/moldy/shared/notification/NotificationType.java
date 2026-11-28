package com.moldy.shared.notification;

public enum NotificationType {

    // Order
    ORDER_CREATED,
    ORDER_PAID,
    ORDER_CONFIRMED,
    ORDER_PREPARING,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    ORDER_COMPLETED,
    ORDER_CANCELLED,

    // Payment / Escrow
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    PAYMENT_EXPIRED,
    ESCROW_RELEASED,
    ESCROW_REFUNDED,

    // Offer
    OFFER_RECEIVED,
    OFFER_ACCEPTED,
    OFFER_REJECTED,
    OFFER_COUNTERED,
    OFFER_EXPIRED,
    OFFER_CANCELLED,

    // Appraisal
    APPRAISAL_REQUESTED,
    APPRAISAL_ASSIGNED,
    APPRAISAL_COMPLETED,
    APPRAISAL_TIMEOUT,

    // Dispute
    DISPUTE_OPENED,
    DISPUTE_RESOLVED,

    // Wallet
    WITHDRAWAL_REQUESTED,
    WITHDRAWAL_SUCCESS,
    WITHDRAWAL_FAILED,

    // Review
    REVIEW_RECEIVED,

    //Account
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED;

    /** Whether this notification type is delivered through transactional email. */
    public boolean sendsEmail() {
        return switch (this) {
            case ORDER_COMPLETED,
                 PAYMENT_SUCCESS,
                 PAYMENT_FAILED,
                 ESCROW_RELEASED,
                 ESCROW_REFUNDED,
                 DISPUTE_OPENED,
                 DISPUTE_RESOLVED,
                 WITHDRAWAL_SUCCESS,
                 WITHDRAWAL_FAILED -> true;
            default -> false;
        };
    }
}
