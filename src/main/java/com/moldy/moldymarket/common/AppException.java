package com.moldy.moldymarket.common;

import lombok.Getter;

/**
 * Exception dùng chung cho toàn bộ dự án Moldy Market.
 *
 * Cách dùng:
 *   throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
 *
 * GlobalExceptionHandler bắt AppException và map sang HTTP response
 * tương ứng với ErrorCode.status và ErrorCode.message.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
