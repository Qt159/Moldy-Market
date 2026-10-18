package com.moldy.moldymarket.notification.dto;

import java.util.List;

// nextToken null = đã hết trang.

public record NotificationPageResponse(
        List<NotificationRecord> items,
        String nextToken,   
        int pageSize        // số item thực tế 
) {}
