package com.moldy.moldymarket.common.response;


import java.time.Instant;
public record ApiResponse<T> (
    boolean success,
    String code,
    String message,
    T data,
    Instant timestamp
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, "SUCCESS", message, data, Instant.now());
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("Request completed successfully", data);
    }

    public static <T> ApiResponse<T> failure(
            String code,
            String message,
            T data
    ) {
        return new ApiResponse<>(
                false,
                code,
                message,
                data,
                Instant.now()
        );
    }
}
