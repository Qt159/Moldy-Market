package com.moldy.moldymarket.notification.exception;

/**
 * Ném khi thao tác không hợp lệ theo trạng thái hiện tại:
 * - softDelete notification đã bị xóa
 * - restore notification chưa bị xóa
 * → HTTP 409 Conflict
 */
public class NotificationConflictException extends RuntimeException {
    public NotificationConflictException(String message) {
        super(message);
    }
}
