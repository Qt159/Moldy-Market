# Chat names and customer-chat policy

Read/unread follow-up: docs/testing/CHAT-READ-UNREAD.md supersedes this step's timestamp-only read contract and schema/UI limitations. The names and customer-chat business policy below remain in force.

2026-11-21 — IMPLEMENTED_LOCAL. Chu explicitly authorized direct Backend/frontend changes and delegated the business review for this chat task. No further copying is needed.

## Current rules

- Chat remains between two accounts; product/store entry targets the store owner, including staff-posted products. This is not a shared staff inbox or an internal team chat.
- A user cannot start a conversation with themselves. Both accounts must exist and be ACTIVE to start/send.
- A current staff member and their store owner cannot create or send customer chat with each other, in either direction. Staff may contact other stores. This supersedes the earlier implementation that allowed a personal staff-owner pair from the storefront button.
- Existing history is preserved. The pair becomes read-only while the employment relation exists; no conversation/message is deleted or silently marked CLOSED. Former staff may chat again if the room is ACTIVE and both accounts are available. Explicit CLOSED rooms stay closed.
- Reading history and marking read require actual participation. Foreign accounts cannot use either action. There is no new admin/public user-directory endpoint for fetching names.

## Response and implementation

ConversationResponse keeps its original fields and adds participant1Name, participant2Name, canSend and sendBlockedReason. Names come from User.fullName, trimmed, and may be absent for missing/blank historical data. Email/phone are not returned. The frontend uses the participant IDs to choose the peer/header/sender name, never substitutes private contact data and retains a neutral fallback.

ChatAccessPolicy loads users and staff-store owner relations in batches. ConversationService reuses this context for every inbox row, rather than looking up each name separately. Membership collections cover all rows so inconsistent historical multi-store records cannot hide a staff-owner relation. MessageService rechecks the same policy on every send; UI flags are not an authorization boundary.

New REST error names: CHAT_STORE_MEMBER and CHAT_PARTICIPANT_UNAVAILABLE. Corresponding STOMP codes are CHAT_005 and CHAT_006. canSend=false also covers CLOSED rooms; the frontend displays the server reason and disables composing. A stale client that tries to send is still rejected by the server.

Creation locks both existing user rows in sorted UUID order before checking/creating the pair. A->B and B->A therefore serialize and reuse one conversation. Existing duplicate conversations are not merged/deleted; the oldest stable row is reused for new start requests, while all historical IDs remain readable to their participants.

The previously stubbed PATCH conversations/{id}/read now calls the participant-checked service and returns 204 after saving only the caller's timestamp. The frontend does not yet display read receipts. History rejects negative page, size outside 1..100 and offsets exceeding the JPA integer bound with INVALID_REQUEST; sorting includes id for equal timestamps.

No schema migration, dependency or token-storage change was needed. The public store ownerId DTO copied by Chu was verified and its HTTP regression test installed.

## Verification

Full Maven verify/package: 311 tests, 310 passed, one pre-existing disabled QA-010, no failures/errors. Tests include batch names, no contact details, own-store membership in both directions, other-shop chat, unavailable/self/missing targets, historical multiple memberships, closed-room behavior, actual read timestamps, invalid paging, live SockJS/STOMP rejection, and concurrent pair creation with two independent transactions. Existing send/rollback/checkout tests remain green. Synthetic committed fixtures clean only their own generated IDs.

Frontend full suite passed 437/437 tests in 48 files; format, lint, format check and build passed. Tests cover real peer/sender labels, name fallback/privacy, read-only history, staff own-shop entry block, external-shop entry and business error messages. No tests were disabled and no assertions/timeouts were weakened.

The running MoldyMarketApplication was rerun through IntelliJ (not replaced with a detached server), and the new process returned HTTP 200 from the public categories endpoint. Frontend UI remains for Chu to inspect; no real-user messages were sent by the assistant. No commit/push.

Historical copy guides under docs/testing describe earlier steps. Do not copy old service/controller snapshots over the current policy implementation.
