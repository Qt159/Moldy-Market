package com.moldy.moldymarket.notification.controller;

import com.moldy.moldymarket.common.ApiResponse;
import com.moldy.moldymarket.notification.dto.NotificationPageResponse;
import com.moldy.moldymarket.notification.dto.NotificationRecord;
import com.moldy.moldymarket.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller cho Notification module.
 *
 * Không có @ExceptionHandler cục bộ — mọi exception xử lý tập trung
 * tại GlobalExceptionHandler (common package).
 *
 * TODO: Khi auth module hoàn thiện, thay @RequestHeader("X-User-Id")
 *       bằng @AuthenticationPrincipal UserDetails.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** GET /api/v1/notifications — danh sách có phân trang cursor-based. */
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getAll(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String token) {

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(ApiResponse.success(service.getPage(userId, safeLimit, token)));
    }

    /** GET /api/v1/notifications/{id} — chi tiết một notification. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationRecord>> getById(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(service.getById(userId, id)));
    }

    /** PATCH /api/v1/notifications/{id}/read — đánh dấu đã đọc (idempotent). */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String id) {
        service.markAsRead(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/v1/notifications/{id} — soft delete. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String id) {
        service.softDelete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** PATCH /api/v1/notifications/{id}/restore — phục hồi notification đã xóa. */
    @PatchMapping("/{id}/restore")
    public ResponseEntity<Void> restore(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String id) {
        service.restore(userId, id);
        return ResponseEntity.noContent().build();
    }
}
