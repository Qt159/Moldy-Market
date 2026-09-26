package com.moldy.moldymarket.notification.exception;

/*  notification đã bị xóa
    restore notification chưa bị xóa
    -> 409 Conflict
 */
public class NotificationConflictException extends RuntimeException {
    public NotificationConflictException(String message) {
        super(message);
    }
}
