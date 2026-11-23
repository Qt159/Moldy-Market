# Notification transactional outbox

## Event lifecycle

1. A transactional business service publishes its existing Spring domain event.
2. `NotificationEventListener` receives it synchronously, maps it with `NotificationEventFactory`, and invokes `OutboxService` before the business transaction completes.
3. The listener, business writes, and JSONB outbox inserts share the current PostgreSQL transaction. An outbox insert/serialization failure aborts the business transaction. `OutboxService` requires an active transaction (`MANDATORY`).
4. The scheduled publisher claims a bounded batch with `FOR UPDATE SKIP LOCKED`, assigns a lease and commits the claim transaction.
5. After the claim transaction completes, it sends each saved event through the existing SQS publisher. An acknowledged message is marked `PUBLISHED`; failures increment `retry_count`, schedule exponential backoff, and eventually move to `FAILED` for operator review.

## Delivery and duplicates

Delivery is **at least once**, not exactly once. If SQS accepts a message and the process stops before the row is marked published, the lease expires and the event can be sent again. `notification_id` is unique in the outbox, and the existing Lambda consumer conditionally inserts notification history by its deterministic notification ID, so duplicate SQS deliveries do not create duplicate notification rows.

Claims are leases, not locks held over network I/O. Rows with an expired lease become eligible again. A worker whose lease was superseded cannot mark the row published because state updates require its claim token.

## Configuration

Environment variables (defaults are in `application.properties`):

| Variable | Default | Purpose |
|---|---:|---|
| `NOTIFICATION_OUTBOX_INTERVAL` | `5s` | Delay between publisher runs |
| `NOTIFICATION_OUTBOX_BATCH_SIZE` | `20` | Maximum rows claimed per run (1–100) |
| `NOTIFICATION_OUTBOX_MAX_RETRIES` | `12` | Attempts before marking a row `FAILED` |
| `NOTIFICATION_OUTBOX_INITIAL_RETRY_DELAY` | `5s` | First retry delay |
| `NOTIFICATION_OUTBOX_MAX_RETRY_DELAY` | `1h` | Backoff ceiling |
| `NOTIFICATION_OUTBOX_CLAIM_LEASE` | `2m` | Recovery window for abandoned claims |

## Local testing

Run `mvn test` with Docker available for the PostgreSQL Testcontainers tests. They apply the same migration as the application. Notification publisher tests mock SQS; they do not send real messages. On an existing database without Flyway history, Flyway baselines at version 0 so `V1__create_notification_outbox.sql` is applied.

Before production rollout, verify the migration on a backup/restore copy of the target database and monitor outbox counts by `status`, oldest pending age, retry counts, and `FAILED` rows. Keep published rows for audit/diagnostics; define a separate retention policy before purging them.
