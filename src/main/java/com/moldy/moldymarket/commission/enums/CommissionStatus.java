package com.moldy.moldymarket.commission.enums;

/**
 * Commission financial lifecycle status
 */
public enum CommissionStatus {
    /**
     * Workflow assigned, awaiting completion
     */
    PENDING,

    /**
     * Workflow completed, commission earned
     */
    EARNED,

    /**
     * Commission paid to appraiser
     */
    PAID,

    /**
     * Commission cancelled (workflow cancelled before completion)
     */
    CANCELLED
}
