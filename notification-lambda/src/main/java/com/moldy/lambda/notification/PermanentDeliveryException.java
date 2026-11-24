package com.moldy.lambda.notification;

/** A provider response that should be acknowledged instead of retried by SQS. */
public class PermanentDeliveryException extends RuntimeException {
    private final int statusCode;

    public PermanentDeliveryException(String message) {
        this(message, 0, null);
    }

    public PermanentDeliveryException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public PermanentDeliveryException(String message, Throwable cause) {
        this(message, 0, cause);
    }

    private PermanentDeliveryException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
