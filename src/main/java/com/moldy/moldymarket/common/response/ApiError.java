package com.moldy.moldymarket.common.response;

import java.util.Map;

public record ApiError(
        String path,
        Map<String, String> validationErrors
) {
    public static ApiError of(String path) {
        return new ApiError(path, null);
    }

    public static ApiError of(
            String path,
            Map<String, String> validationErrors
    ) {
        return new ApiError(path, validationErrors);
    }
}
