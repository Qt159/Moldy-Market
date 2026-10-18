package com.moldy.moldymarket.common;

/**
 * Wrapper response format dùng chung cho tất cả API của Moldy Market.
 *
 * @param <T> Kiểu dữ liệu của trường result
 */
public record ApiResponse<T>(
        String code,
        String message,
        T result
) {
    /** Tạo response thành công với data. */
    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>("SUCCESS", "OK", result);
    }

    /** Tạo response thành công với message tùy chỉnh và data. */
    public static <T> ApiResponse<T> success(String message, T result) {
        return new ApiResponse<>("SUCCESS", message, result);
    }

    /** Tạo response lỗi không có data. */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
