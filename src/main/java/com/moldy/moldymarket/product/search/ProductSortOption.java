package com.moldy.moldymarket.product.search;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;

import java.util.Locale;

public enum ProductSortOption {
    RELEVANCE,
    NEWEST,
    PRICE_ASC,
    PRICE_DESC;

    /** null nếu client không gửi, để service tự chọn mặc định. */
    public static ProductSortOption fromParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Unsupported sort. Allowed: relevance, newest, price_asc, price_desc");
        }
    }
}
