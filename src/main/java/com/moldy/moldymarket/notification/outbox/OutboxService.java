package com.moldy.moldymarket.notification.outbox;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.moldy.shared.notification.NotificationEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(List<NotificationEvent> events) {
        for (NotificationEvent event : events) {
            enqueue(event);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(NotificationEvent event) {
        if (event == null) return;
        try {
            String payload = objectMapper.writeValueAsString(event);
            String aggregateType = hasText(event.referenceType())
                    ? event.referenceType() : "NOTIFICATION";
            String aggregateId = hasText(event.referenceId())
                    ? event.referenceId() : event.notificationId();

            repository.enqueue(UUID.randomUUID(), event, payload, aggregateType, aggregateId);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize notification outbox payload", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
