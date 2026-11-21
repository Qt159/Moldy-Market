package com.moldy.lambda.notification;

// Đánh dấu lỗi có thể retry — SQS sẽ gửi lại message.
public class RetryableException extends RuntimeException {
    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
