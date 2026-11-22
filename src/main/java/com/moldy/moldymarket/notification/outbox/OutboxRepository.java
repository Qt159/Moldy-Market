package com.moldy.moldymarket.notification.outbox;

import com.moldy.shared.notification.NotificationEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final NamedParameterJdbcTemplate jdbc;

    public OutboxRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(
            UUID id,
            NotificationEvent event,
            String payload,
            String aggregateType,
            String aggregateId
    ) {
        jdbc.update("""
                INSERT INTO outbox_events (
                    id, notification_id, aggregate_type, aggregate_id, event_type,
                    payload, status, retry_count, next_attempt_at, created_at
                ) VALUES (
                    :id, :notificationId, :aggregateType, :aggregateId, :eventType,
                    CAST(:payload AS jsonb), 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                ON CONFLICT (notification_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("notificationId", event.notificationId())
                .addValue("aggregateType", aggregateType)
                .addValue("aggregateId", aggregateId)
                .addValue("eventType", event.type().name())
                .addValue("payload", payload));
    }

    /** Claims are committed before this method returns; network calls happen afterwards. */
    @Transactional
    public List<ClaimedOutboxEvent> claimBatch(
            int batchSize,
            Duration lease,
            UUID claimToken
    ) {
        return jdbc.query("""
                WITH candidates AS (
                    SELECT id
                    FROM outbox_events
                    WHERE status = 'PENDING'
                      AND next_attempt_at <= CURRENT_TIMESTAMP
                      AND (locked_until IS NULL OR locked_until <= CURRENT_TIMESTAMP)
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                )
                UPDATE outbox_events AS o
                SET lock_token = :claimToken,
                    locked_until = CURRENT_TIMESTAMP + (:leaseSeconds * INTERVAL '1 second')
                FROM candidates
                WHERE o.id = candidates.id
                RETURNING o.id, o.notification_id, o.payload::text AS payload,
                          o.retry_count
                """, new MapSqlParameterSource()
                .addValue("batchSize", batchSize)
                .addValue("claimToken", claimToken)
                .addValue("leaseSeconds", lease.toSeconds()),
                (rs, rowNum) -> new ClaimedOutboxEvent(
                        rs.getObject("id", UUID.class),
                        rs.getString("notification_id"),
                        rs.getString("payload"),
                        rs.getInt("retry_count"),
                        claimToken));
    }

    public boolean markPublished(UUID id, UUID claimToken) {
        return jdbc.update("""
                UPDATE outbox_events
                SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP,
                    locked_until = NULL, lock_token = NULL, last_error = NULL
                WHERE id = :id AND status = 'PENDING' AND lock_token = :claimToken
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("claimToken", claimToken)) == 1;
    }

    public boolean recordFailure(
            UUID id,
            UUID claimToken,
            int maxRetries,
            Duration delay,
            String safeError
    ) {
        String error = safeError == null ? "Publish failed" : safeError;
        if (error.length() > MAX_ERROR_LENGTH) {
            error = error.substring(0, MAX_ERROR_LENGTH);
        }
        return jdbc.update("""
                UPDATE outbox_events
                SET retry_count = retry_count + 1,
                    status = CASE WHEN retry_count + 1 >= :maxRetries THEN 'FAILED' ELSE 'PENDING' END,
                    next_attempt_at = CURRENT_TIMESTAMP + (:delaySeconds * INTERVAL '1 second'),
                    last_error = :lastError,
                    locked_until = NULL,
                    lock_token = NULL
                WHERE id = :id AND status = 'PENDING' AND lock_token = :claimToken
                """, new MapSqlParameterSource()
                .addValue("maxRetries", maxRetries)
                .addValue("delaySeconds", delay.toSeconds())
                .addValue("lastError", error)
                .addValue("id", id)
                .addValue("claimToken", claimToken)) == 1;
    }
}
