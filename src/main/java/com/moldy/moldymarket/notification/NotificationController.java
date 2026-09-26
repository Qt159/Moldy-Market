package com.moldy.moldymarket.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /**
     * GET /api/notifications
     * Lấy tất cả notification của user hiện tại (chưa bị xóa).
     */
    @GetMapping
    public ResponseEntity<List<NotificationRecord>> getAll(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(service.getAllByUser(userId));
    }

    /**
     * GET /api/notifications/{id}
     * Lấy một notification theo id.
     */
    @GetMapping("/{id}")
    public ResponseEntity<NotificationRecord> getById(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        return ResponseEntity.ok(service.getById(userId, id));
    }

    /**
     * PATCH /api/notifications/{id}/read
     * Đánh dấu notification là đã đọc.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        service.markAsRead(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/notifications/{id}
     * Soft delete — set deletedAt, không xóa hẳn.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        service.softDelete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/notifications/{id}/restore
     * Phục hồi notification đã bị xóa mềm.
     */
    @PatchMapping("/{id}/restore")
    public ResponseEntity<Void> restore(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        service.restore(userId, id);
        return ResponseEntity.noContent().build();
    }
}
