package com.moldy.moldymarket.notification.dto;

import java.util.List;

/**
 * Response trả về cho GET /api/notifications
 * nextToken null = đã hết trang.
 */
public record NotificationPageResponse(
        List<NotificationRecord> items,
        String nextToken,   // Base64 cursor, null nếu không còn trang
        int pageSize        // số item thực tế trả về trong trang này
) {}
