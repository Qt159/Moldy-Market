package com.moldy.moldymarket.notification.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.notification.dto.NotificationPageResponse;
import com.moldy.moldymarket.notification.dto.NotificationPaginationToken;
import com.moldy.moldymarket.notification.dto.NotificationRecord;
import com.moldy.moldymarket.notification.repository.NotificationRepository;
import com.moldy.moldymarket.notification.repository.NotificationRepository.StoredNotification;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public NotificationPageResponse getPage(String userId, int limit, String token) {
        var startKey = NotificationPaginationToken.decode(token, userId);
        var result = repository.findByUserId(userId, limit, startKey);
        String nextToken = NotificationPaginationToken.encode(result.lastEvaluatedKey(), userId);
        return NotificationPageResponse.builder()
                .items(result.items())
                .nextToken(nextToken)
                .limit(limit)
                .hasMore(nextToken != null)
                .build();
    }

    public NotificationRecord getById(String userId, String notificationId) {
        return find(userId, notificationId).response();
    }

    public void markAsRead(String userId, String notificationId) {
        StoredNotification notification = find(userId, notificationId);
        if (!notification.response().isRead()) repository.markAsRead(userId, notification.sortKey());
    }

    public void softDelete(String userId, String notificationId) {
        StoredNotification notification = find(userId, notificationId);
        if (notification.response().isDeleted()) throw new AppException(ErrorCode.NOTIFICATION_ALREADY_DELETED);
        repository.softDelete(userId, notification.sortKey());
    }

    public void restore(String userId, String notificationId) {
        StoredNotification notification = find(userId, notificationId);
        if (!notification.response().isDeleted()) throw new AppException(ErrorCode.NOTIFICATION_NOT_DELETED);
        repository.restore(userId, notification.sortKey());
    }

    private StoredNotification find(String userId, String notificationId) {
        return repository.findById(userId, notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));
    }
}
