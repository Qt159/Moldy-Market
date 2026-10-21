package com.moldy.moldymarket.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // ---------------------------------------------------------------
    // System
    // ---------------------------------------------------------------
    UNCATEGORIZED_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi không xác định"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống nội bộ"),

    // ---------------------------------------------------------------
    // Auth
    // ---------------------------------------------------------------
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Chưa xác thực, vui lòng đăng nhập"),
    UNAUTHORIZED(HttpStatus.FORBIDDEN, "Không có quyền truy cập"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Token không hợp lệ"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token đã hết hạn"),

    // ---------------------------------------------------------------
    // User
    // ---------------------------------------------------------------
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Người dùng không tồn tại"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã tồn tại"),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),
    NEW_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "Mật khẩu mới không được trùng với mật khẩu cũ"),

    // ---------------------------------------------------------------
    // Category
    // ---------------------------------------------------------------
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Danh mục không tồn tại"),
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "Danh mục đang có danh mục con, không thể xóa"),
    CATEGORY_HAS_LISTINGS(HttpStatus.CONFLICT, "Danh mục đang có sản phẩm liên kết, không thể xóa"),
    CATEGORY_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Danh mục không còn hoạt động"),
    CATEGORY_NOT_LEAF(HttpStatus.UNPROCESSABLE_ENTITY, "Sản phẩm phải được gán vào danh mục con (subcategory)"),
    CATEGORY_MAX_DEPTH(HttpStatus.BAD_REQUEST, "Danh mục chỉ được phép tối đa 2 cấp"),
    CATEGORY_SELF_PARENT(HttpStatus.BAD_REQUEST, "Danh mục không thể là cha của chính nó"),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "Slug đã tồn tại trong cùng danh mục cha"),

    // ---------------------------------------------------------------
    // Listing / Product (placeholder — mở rộng khi build module)
    // ---------------------------------------------------------------
    LISTING_NOT_FOUND(HttpStatus.NOT_FOUND, "Sản phẩm không tồn tại"),

    // ---------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Thông báo không tồn tại"),
    NOTIFICATION_ALREADY_DELETED(HttpStatus.CONFLICT, "Thông báo đã bị xóa"),
    NOTIFICATION_NOT_DELETED(HttpStatus.CONFLICT, "Thông báo chưa bị xóa"),

    // ---------------------------------------------------------------
    // General validation
    // ---------------------------------------------------------------
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    DATA_INTEGRITY_VIOLATION(HttpStatus.BAD_REQUEST, "Vi phạm ràng buộc dữ liệu"),
    INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "Thông tin phân trang không hợp lệ"),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "Khoảng ngày không hợp lệ");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
