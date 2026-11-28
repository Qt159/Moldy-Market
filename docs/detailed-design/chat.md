# Detailed Design — Realtime Chat (`chat` module)

> **Status:** IMPLEMENTED & VERIFIED (2026-11-27, Backend `main` `f3ca06a`)
> **Package:** `com.moldy.moldymarket.chat`
> **Transport:** REST (`/api/v1/conversations`) + STOMP over SockJS (`/chat`)

---

## 1. Architecture Summary

- **1-on-1 conversations between two user accounts** (`participant1Id`, `participant2Id`). Store-product chats always target the store's `ownerId` (`PublicStoreResponse.ownerId`), even for staff-posted listings.
- **Access & Business Policy (`ChatAccessPolicy`):**
  - Blocks self-chat (`CHAT_001`), inactive/missing peers (`CHAT_006`), and customer chat between a store owner and their own active staff (`CHAT_005` `CHAT_STORE_MEMBER`).
  - Historical threads between an owner and current staff remain readable (`canSend = false`, `sendBlockedReason = "CHAT_STORE_MEMBER"`).
  - Conversation creation locks both user rows in sorted UUID order to prevent concurrent `A→B` / `B→A` duplicate conversations.
- **Bounded Read Cursors:**
  - `Conversation` stores `lastReadMessageIdParticipant1`, `lastReadMessageIdParticipant2` (plus legacy timestamps).
  - `PATCH /api/v1/conversations/{id}/read` with `{ messageId }` monotonically advances only the caller's cursor under a row lock and emits a private STOMP receipt to `/user/queue/chat-reads`.
  - Unread counts and latest message previews are batch-aggregated in `ConversationService.getUserConversationList`.

---

## 2. REST Endpoints (`/api/v1/conversations`)

| Method | Path | Request / Params | Response |
|---|---|---|---|
| `POST` | `/api/v1/conversations` | `{ targetUserId }` | `ApiResponse<ConversationResponse>` (finds existing pair or creates new). |
| `GET` | `/api/v1/conversations` | — | `ApiResponse<List<ConversationResponse>>` (includes participant names, `canSend`, `sendBlockedReason`, cursors, `unreadCount`, `lastMessageContent`). |
| `GET` | `/api/v1/conversations/{id}/messages` | `?page=0&size=20` (0-based Spring Page) | `ApiResponse<Page<MessageResponse>>` ordered `sentAt DESC`. |
| `PATCH` | `/api/v1/conversations/{id}/read` | `{ messageId }` | `200 OK`, advances read cursor and notifies peer. |

---

## 3. WebSocket / STOMP Contract (`/chat`)

- **Endpoint:** `/chat` (SockJS enabled; Vite dev proxy forwards `/chat` without intercepting SPA `/chat` routes).
- **Authentication (`StompAuthInterceptor`):** Validates JWT Bearer token on `CONNECT`, `SEND`, and `SUBSCRIBE`, enforces destination allowlist and room membership.
- **Client → Server:** `/app/conversations/{conversationId}/send` with `SendMessageRequest { type: TEXT | IMAGE | FILE, content, attachmentUrl?, attachedProductId? }`.
- **Server → Client (User Queues):**
  - `/user/queue/messages`: `MessageResponse` echoed to both sender and recipient.
  - `/user/queue/chat-reads`: `ChatReadReceipt` on committed read-cursor advance.
  - `/user/queue/errors`: `ChatErrorResponse` sent only to the originating session.

---

## 4. Structured Offer Cards in Chat (`OfferChatEventListener`)

`Message` includes two nullable relational links:
- `attached_product_id` (`UUID`): product context attached to a message.
- `offer_id` (`UUID`): links the message to an `Offer` entity.

`MessageResponse` exposes both `attachedProductId` and `offerId`:
```json
{
  "id": 42,
  "conversationId": "uuid",
  "senderId": "uuid",
  "type": "TEXT",
  "content": "Đề nghị trả giá 36.000.000 ₫",
  "attachment": null,
  "attachedProductId": "uuid",
  "offerId": "uuid",
  "sentAt": "2026-11-27T08:30:00Z"
}
```

### Why `type = TEXT` + `offerId != null`?
- Preserves existing PostgreSQL `message_type_check` constraint (`TEXT`, `IMAGE`, `FILE`) with zero risky DDL on existing databases.
- Populates `content` with a clean Vietnamese summary so the conversation inbox sidebar (`lastMessageContent`) displays human-readable previews automatically.
- On `OfferEvent(PENDING)` (create or counter), `OfferChatEventListener` inserts a new `Message` row with `offerId` and broadcasts it via STOMP `/user/queue/messages` to both buyer and store owner.
- On `ACCEPTED`, `REJECTED`, `CANCELLED`, or `EXPIRED`, it updates the existing `offerId` message preview and broadcasts via STOMP so both clients immediately refresh the live `ChatOfferCard` and `ChatPinnedOfferBar` without page reload.
