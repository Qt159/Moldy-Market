package com.moldy.moldymarket.notification.service;

import com.moldy.moldymarket.notification.dto.NotificationPageResponse;
import com.moldy.moldymarket.notification.dto.NotificationPaginationToken;
import com.moldy.moldymarket.notification.dto.NotificationRecord;
import com.moldy.moldymarket.notification.exception.NotificationConflictException;
import com.moldy.moldymarket.notification.exception.NotificationNotFoundException;
import com.moldy.moldymarket.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    // ── GET ALL (phân trang) ──────────────────────────────────────────
    public NotificationPageResponse getPage(String userId, int limit, String token) {
        var startKey = NotificationPaginationToken.decode(token, userId);
        var result = repository.findByUserId(userId, limit, startKey);
        var nextToken = NotificationPaginationToken.encode(result.lastEvaluatedKey(), userId);

        return new NotificationPageResponse(result.items(), nextToken, result.items().size());
    }

    // ── GET BY ID ─────────────────────────────────────────────────────
    public NotificationRecord getById(String userId, String notificationId) {
        return repository.findById(userId, notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    // ── MARK AS READ ──────────────────────────────────────────────────
    public void markAsRead(String userId, String notificationId) {
        NotificationRecord record = getById(userId, notificationId);
        if (record.isRead()) return; // đã đọc rồi, không tốn WCU
        repository.markAsRead(userId, buildSk(record));
    }

    // ── SOFT DELETE ───────────────────────────────────────────────────
    public void softDelete(String userId, String notificationId) {
        NotificationRecord record = getById(userId, notificationId);
        if (record.deletedAt() != null) {
            throw new NotificationConflictException("Notification already deleted: " + notificationId);
        }
        repository.softDelete(userId, buildSk(record));
    }

    // ── RESTORE ───────────────────────────────────────────────────────
    public void restore(String userId, String notificationId) {
        NotificationRecord record = getById(userId, notificationId);
        if (record.deletedAt() == null) {
            throw new NotificationConflictException("Notification is not deleted: " + notificationId);
        }
        repository.restore(userId, buildSk(record));
    }

    // ── Helper ────────────────────────────────────────────────────────
    private String buildSk(NotificationRecord record) {
        return "NOTIFICATION#" + record.createdAt() + "#" + record.notificationId();
    }
}
