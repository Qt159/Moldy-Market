package com.moldy.moldymarket.notification.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.notification.dto.NotificationPageResponse;
import com.moldy.moldymarket.notification.dto.NotificationRecord;
import com.moldy.moldymarket.notification.service.NotificationService;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    // phân trang cursor-based
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getAll(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String token) {

        String userId = userDetails.getUserId().toString();
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(ApiResponse.success(service.getPage(userId, safeLimit, token)));
    }


    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationRecord>> getById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id) {

        return ResponseEntity.ok(ApiResponse.success(
                service.getById(userDetails.getUserId().toString(), id)));
    }


    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id) {

        service.markAsRead(userDetails.getUserId().toString(), id);
        return ResponseEntity.noContent().build();
    }

 
 
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id) {

        service.softDelete(userDetails.getUserId().toString(), id);
        return ResponseEntity.noContent().build();
    }


    @PatchMapping("/{id}/restore")
    public ResponseEntity<Void> restore(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String id) {

        service.restore(userDetails.getUserId().toString(), id);
        return ResponseEntity.noContent().build();
    }
}
