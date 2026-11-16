package com.moldy.moldymarket.notification.service;

import com.moldy.moldymarket.notification.dto.NotificationPageResponse;
import com.moldy.moldymarket.notification.dto.NotificationRecord;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    public NotificationPageResponse getPage(String userId, int limit, String token) {
        // Implementation will be added
        return NotificationPageResponse.builder()
                .items(java.util.Collections.emptyList())
                .limit(limit)
                .hasMore(false)
                .build();
    }

    public NotificationRecord getById(String userId, String notificationId) {
        // Implementation will be added
        return NotificationRecord.builder()
                .id(notificationId)
                .userId(userId)
                .build();
    }

    public void markAsRead(String userId, String notificationId) {
        // Implementation will be added
    }

    public void softDelete(String userId, String notificationId) {
        // Implementation will be added
    }

    public void restore(String userId, String notificationId) {
        // Implementation will be added
    }
}
