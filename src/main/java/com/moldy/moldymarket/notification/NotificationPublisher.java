package com.moldy.moldymarket.notification;

import com.moldy.shared.notification.NotificationEvent;

public interface NotificationPublisher {
    void publishNotification(NotificationEvent event);
}
