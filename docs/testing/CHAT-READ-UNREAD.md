# Chat read/unread

2026-11-21 — IMPLEMENTED_LOCAL. Chu approved read/unread badges and sender read receipts, under the existing authorization for direct chat edits.

## Contract

- ConversationResponse adds nullable lastReadMessageIdParticipant1/2 and caller-scoped unreadCount. Existing fields/names/send policy remain.
- PATCH /api/v1/conversations/{id}/read now requires JSON {"messageId": positiveLong}. Missing/invalid body is 400; success remains 204. The ID must belong to this room and the caller must participate. It acknowledges only that prefix, never messages that commit later.
- Read cursors advance monotonically under the same conversation row lock used by sends. Only the caller's cursor/timestamp changes. Repeated/older acknowledgements do not emit another receipt. Read-only/CLOSED history remains readable to its participants.
- Unread count includes only messages from the other participant after the caller's cursor. Own messages and empty conversations count zero. One aggregate query supplies counts for the entire inbox, rather than querying each room. Legacy timestamp-only reads remain respected until their first ID acknowledgement; conversion preserves the previously read prefix.
- New private STOMP subscription /user/queue/chat-reads receives {conversationId,readerId,messageId,readAt} for both participants after transaction commit. A rollback emits nothing. Raw broker/other-account subscriptions and client-published receipts are denied. Read events trigger canonical REST refetch; REST remains authoritative if delivery is missed.

## Schema and rollout

Additive migration: src/main/resources/db/migration/V20261121__chat_read_cursors.sql. It adds nullable BIGINT last_read_message_id_participant1/2 to conversation, with no history deletion/backfill. Local spring.jpa.hibernate.ddl-auto=update creates these columns as verified by PostgreSQL integration tests. There is no automatic Flyway runner in this project: apply the SQL through the normal migration process on databases using validate/none before deploying this version. Deploy Backend before frontend because the PATCH body and response fields are coordinated changes.

## Frontend behavior

MainLayout owns a single STOMP connection per current session, shared with ChatPage through context. Menu unread total works outside chat, and individual rooms show badges. A foreground REST poll every 30 seconds repairs missed events; changing token/logout closes the old transport and rejects stale callbacks.

A read acknowledgement requires successful history, a visible and focused browser document, and at least 50% intersection of the latest loaded incoming bubble. Mounting/selecting a room alone, a background tab, own outgoing messages, or failed history does not acknowledge incoming messages. An acknowledgement in flight is bounded by its captured message ID; a newer bubble needs its own visibility observation. There is one request in flight; failures preserve unread state and offer explicit retry. No automatic retry or optimistic clearing/receipt.

Outgoing bubbles display Read only when the peer's server-provided ID cursor reaches their ID in that same room. The caller's own cursor, client time, send echo and delivered state cannot manufacture a peer read receipt. The badge indicates a read prefix, not a claim that every older bubble was individually inspected.

## Verification

Full Maven verify/package: 314 tests, 313 passed, one pre-existing disabled QA-010, zero failures/errors. Added HTTP/PostgreSQL/STOMP coverage for counts, equal-timestamp IDs, bounded/monotonic reads, caller isolation, invalid/foreign-room messages, missing body, legacy conversion, CLOSED reads, private receipts and rollback suppression. Existing send, policy and checkout tests remain green.

Frontend full suite: 449/449 tests in 49 files. Format, lint, format check and build passed. Coverage includes focus/hidden/viewport gating, serialized new arrivals, explicit retry, no optimistic clearing, logout races, global badge updates outside chat, one shared connection and server-driven peer receipts. No assertions, timeouts or test thresholds were weakened.

MoldyMarketApplication was rerun in IntelliJ; PID 16268 uses verified target/classes and categories/SockJS info returned HTTP 200. Manual two-account UI verification remains with Chu; no real-user messages were sent by the assistant. No commit/push.
