package com.moldy.moldymarket.notification.controller;

import com.moldy.moldymarket.notification.dto.NotificationPageResponse;
import com.moldy.moldymarket.notification.dto.NotificationRecord;
import com.moldy.moldymarket.notification.exception.NotificationConflictException;
import com.moldy.moldymarket.notification.exception.NotificationNotFoundException;
import com.moldy.moldymarket.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /**
     * GET /api/notifications?limit=20&token=xxx
     *
     * userId lấy từ header X-User-Id trong staging (không có auth module).
     * TODO: Khi auth module xong, đổi lại thành @AuthenticationPrincipal String userId
     * và xóa @RequestHeader("X-User-Id").
     */
    @GetMapping
    public ResponseEntity<NotificationPageResponse> getAll(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String token) {

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(service.getPage(userId, safeLimit, token));
    }

    /**
     * GET /api/notifications/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<NotificationRecord> getById(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String id) {
        return ResponseEntity.ok(service.getById(userId, id));
    }

    /**
     * PATCH /api/notifications/{id}/read
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String id) {
        service.markAsRead(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/notifications/{id}
     * Soft delete.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String id) {
        service.softDelete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/notifications/{id}/restore
     */
    @PatchMapping("/{id}/restore")
    public ResponseEntity<Void> restore(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String id) {
        service.restore(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Exception handlers ────────────────────────────────────────────

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotificationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(NotificationConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(NotificationConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    public record ErrorResponse(String code, String message) {}
}
