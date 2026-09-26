package com.moldy.moldymarket.notification;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public List<NotificationRecord> getAllByUser(String userId) {
        return repository.findAllByUserId(userId);
    }

    public NotificationRecord getById(String userId, String notificationId) {
        return repository.findById(userId, notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    public void markAsRead(String userId, String notificationId) {
        NotificationRecord record = getById(userId, notificationId);
        String sk = buildSk(record);
        repository.markAsRead(userId, sk);
    }

    public void softDelete(String userId, String notificationId) {
        NotificationRecord record = getById(userId, notificationId);
        if (record.deletedAt() != null) {
            throw new IllegalStateException("Notification already deleted");
        }
        repository.softDelete(userId, buildSk(record));
    }

    public void restore(String userId, String notificationId) {
        NotificationRecord record = getById(userId, notificationId);
        if (record.deletedAt() == null) {
            throw new IllegalStateException("Notification is not deleted");
        }
        repository.restore(userId, buildSk(record));
    }

    // SK = NOTIFICATION#{createdAt}#{notificationId}
    private String buildSk(NotificationRecord record) {
        return "NOTIFICATION#" + record.createdAt() + "#" + record.notificationId();
    }
}
