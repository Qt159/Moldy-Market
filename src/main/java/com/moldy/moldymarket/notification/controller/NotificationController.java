package com.moldy.moldymarket.notification.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.notification.dto.NotificationPageResponse;
import com.moldy.moldymarket.notification.dto.NotificationRecord;
import com.moldy.moldymarket.notification.service.NotificationService;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller cho Notification module.
 *
 * Auth: JWT — userId lấy từ SecurityContext qua @AuthenticationPrincipal.
 * Exception: tất cả xử lý tập trung tại GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /**
     * GET /api/v1/notifications
     * Lấy danh sách notification của user đang đăng nhập, phân trang cursor-based.
     *
     * @param limit  số item mỗi trang (1–50, mặc định 20)
     * @param token  cursor từ response trước, null cho trang đầu
     */
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getAll(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String token) {

        String userId = userDetails.getUserId().toString();
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(ApiResponse.success(service.getPage(userId, safeLimit, token)));
    }

    /**
     * GET /api/v1/notifications/{id}
     * Chi tiết một notification theo notificationId.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationRecord>> getById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id) {

        String userId = userDetails.getUserId().toString();
        return ResponseEntity.ok(ApiResponse.success(service.getById(userId, id)));
    }

    /**
     * PATCH /api/v1/notifications/{id}/read
     * Đánh dấu đã đọc — idempotent.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id) {

        service.markAsRead(userDetails.getUserId().toString(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/notifications/{id}
     * Soft delete — ẩn notification khỏi danh sách.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id) {

        service.softDelete(userDetails.getUserId().toString(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/v1/notifications/{id}/restore
     * Khôi phục notification đã soft delete.
     */
    @PatchMapping("/{id}/restore")
    public ResponseEntity<Void> restore(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id) {

        service.restore(userDetails.getUserId().toString(), id);
        return ResponseEntity.noContent().build();
    }
}
