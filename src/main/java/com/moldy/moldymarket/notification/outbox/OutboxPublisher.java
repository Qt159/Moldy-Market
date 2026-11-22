package com.moldy.moldymarket.notification.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moldy.moldymarket.notification.publisher.NotificationPublisher;
import com.moldy.shared.notification.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class OutboxPublisher {
    private final OutboxRepository repository;
    private final NotificationPublisher publisher;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final int maxRetries;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final Duration claimLease;

    public OutboxPublisher(
            OutboxRepository repository,
            NotificationPublisher publisher,
            ObjectMapper objectMapper,
            @Value("${outbox.publisher.batch-size:20}") int batchSize,
            @Value("${outbox.publisher.max-retries:12}") int maxRetries,
            @Value("${outbox.publisher.initial-retry-delay:5s}") Duration initialDelay,
            @Value("${outbox.publisher.max-retry-delay:1h}") Duration maxDelay,
            @Value("${outbox.publisher.claim-lease:2m}") Duration claimLease) {
        if (batchSize < 1 || batchSize > 100 || maxRetries < 1
                || initialDelay.isNegative() || initialDelay.isZero()
                || maxDelay.compareTo(initialDelay) < 0
                || claimLease.isNegative() || claimLease.isZero()) {
            throw new IllegalArgumentException("Invalid notification outbox configuration");
        }
        if (initialDelay.toSeconds() < 1 || maxDelay.toSeconds() < 1
                || claimLease.toSeconds() < 1) {
            throw new IllegalArgumentException("Outbox delays must be at least one second");
        }
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.claimLease = claimLease;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.interval:5s}")
    public void publishAvailable() {
        UUID token = UUID.randomUUID();
        List<ClaimedOutboxEvent> claimed = repository.claimBatch(batchSize, claimLease, token);
        claimed.forEach(this::publishOne);
    }

    private void publishOne(ClaimedOutboxEvent row) {
        try {
            NotificationEvent event = objectMapper.readValue(row.payload(), NotificationEvent.class);
            publisher.publish(event);
            if (repository.markPublished(row.id(), row.claimToken())) {
                log.info("Outbox event published. outboxId={}, notificationId={}",
                        row.id(), row.notificationId());
            } else {
                log.warn("Outbox claim lost after publish. outboxId={}, notificationId={}",
                        row.id(), row.notificationId());
            }
        } catch (Exception exception) {
            Duration delay = retryDelay(row.retryCount());
            boolean updated = repository.recordFailure(row.id(), row.claimToken(), maxRetries,
                    delay, safeError(exception));
            log.warn("Outbox publish failed. outboxId={}, notificationId={}, retryable={}, delaySeconds={}",
                    row.id(), row.notificationId(), updated && row.retryCount() + 1 < maxRetries,
                    delay.toSeconds());
        }
    }

    private Duration retryDelay(int retryCount) {
        long delay = initialDelay.toSeconds();
        long max = maxDelay.toSeconds();
        for (int i = 0; i < retryCount && delay < max; i++) {
            delay = delay > max / 2 ? max : delay * 2;
        }
        return Duration.ofSeconds(Math.min(delay, max));
    }

    private String safeError(Exception exception) {
        if (exception instanceof AwsServiceException aws && aws.awsErrorDetails() != null
                && aws.awsErrorDetails().errorCode() != null) {
            return exception.getClass().getSimpleName() + ": " + aws.awsErrorDetails().errorCode();
        }
        return exception.getClass().getSimpleName();
    }
}
