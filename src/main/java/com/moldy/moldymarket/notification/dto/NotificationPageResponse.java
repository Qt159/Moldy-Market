package com.moldy.moldymarket.notification.dto.response;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationRecord> items,
        String nextToken,
        int limit,
        boolean hasMore
) {
}