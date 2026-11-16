package com.moldy.moldymarket.notification.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.notification.dto.NotificationPageResponse;
import com.moldy.moldymarket.notification.dto.NotificationRecord;
import com.moldy.moldymarket.notification.service.NotificationService;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Thông báo (Notifications)", description = "Quản lý và xem danh sách thông báo của người dùng")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    // phân trang cursor-based
    @Operation(summary = "Danh sách thông báo", description = "Lấy danh sách thông báo của người dùng hiện tại theo phân trang cursor-based (limit từ 1 đến 50)")
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getAll(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "Số lượng thông báo mỗi trang (mặc định 20, tối đa 50)") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Token phân trang (cursor token) để lấy trang tiếp theo") @RequestParam(required = false) String token) {

        String userId = userDetails.getUserId().toString();
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(ApiResponse.success(service.getPage(userId, safeLimit, token)));
    }

    @Operation(summary = "Xem chi tiết thông báo", description = "Lấy thông tin chi tiết của một thông báo theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationRecord>> getById(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "ID thông báo") @PathVariable String id) {

        return ResponseEntity.ok(ApiResponse.success(
                service.getById(userDetails.getUserId().toString(), id)));
    }

    @Operation(summary = "Đánh dấu thông báo đã đọc", description = "Cập nhật trạng thái thông báo thành đã đọc")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "ID thông báo") @PathVariable String id) {

        service.markAsRead(userDetails.getUserId().toString(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Xóa thông báo", description = "Đánh dấu xóa thông báo (chuyển vào thùng rác/ẩn)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "ID thông báo") @PathVariable String id) {

        service.softDelete(userDetails.getUserId().toString(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Khôi phục thông báo", description = "Khôi phục thông báo đã bị xóa về trạng thái bình thường")
    @PatchMapping("/{id}/restore")
    public ResponseEntity<Void> restore(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "ID thông báo") @PathVariable String id) {

        service.restore(userDetails.getUserId().toString(), id);
        return ResponseEntity.noContent().build();
    }
}
