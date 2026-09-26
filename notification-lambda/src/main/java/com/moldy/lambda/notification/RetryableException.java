package com.moldy.lambda.notification;

/**
 * Đánh dấu lỗi có thể retry — SQS sẽ gửi lại message.
 * Ném exception này khi gặp lỗi tạm thời: AWS throttle, network timeout.
 */
public class RetryableException extends RuntimeException {
    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
