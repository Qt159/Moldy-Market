# Detailed Design — Offer & Price Negotiation (`offer` module)

> **Status:** IMPLEMENTED & VERIFIED (2026-11-27, Backend `main` `f3ca06a`)
> **Package:** `com.moldy.moldymarket.offer`
> **Frontend Consumers:** `Moldy-Market-User-Frontend` (`/products/:productId`, `/offers`, `/store/offers`, `/chat/:conversationId`, `/cart`, `/checkout`)

---

## 1. Overview & Business Rules

Moldy Market supports structured C2C price negotiation on active listings where `Product.allowOffer == true` (default `true`, stored in PostgreSQL with `columnDefinition = "boolean not null default true"`).

### Core Rules (`application.yaml` defaults)
| Config Property | Env Override | Default | Meaning |
|---|---|---|---|
| `offer.min-price-percent` | `OFFER_MIN_PRICE_PERCENT` | `0.75` | Minimum initial offer is **75%** of `Product.price`. |
| `offer.max-price-percent` | `OFFER_MAX_PRICE_PERCENT` | `1.00` | Initial offer must be **strictly below 100%** of `Product.price`. |
| `offer.max-buyer-offers-per-product` | `OFFER_MAX_BUYER_OFFERS_PER_PRODUCT` | `3` | Maximum **3** buyer-side offers (initial + buyer counters) per `(buyerId, productId)`. |
| `offer.response-hours` | `OFFER_RESPONSE_HOURS` | `24` | Each `PENDING` offer expires after **24 hours** if unanswered. |
| `offer.accepted-checkout-hours` | `OFFER_ACCEPTED_CHECKOUT_HOURS` | `24` | After acceptance, buyer has **24 hours** to place the order at the agreed price. |
| `offer.max-page-size` | `OFFER_MAX_PAGE_SIZE` | `50` | Maximum page size for paginated offer endpoints. |

### Guardrails
- **One open offer at a time:** A buyer cannot create a new offer on a product if they already have a `PENDING` or `ACCEPTED` offer on that product (`OFF_005`).
- **Self/store-member block:** Sellers, store owners, and any member of the listing's store (`StoreAccessGuard.isStoreMember`) cannot make an initial offer on their own product (`OFF_002`).
- **Turn enforcement:** Only the opposing side (`actor != offer.side`) can `accept`, `reject`, or `counter` (`supersede`) a `PENDING` offer (`OFF_009`). Only the buyer can `cancel` (`withdraw`) their pending offer.
- **Counter narrowing:** Seller counter must be strictly between the buyer's previous offer and the upper bound (previous seller offer or `originalPrice`). Buyer counter must be strictly between their previous offer and the seller's counter (`OFF_003`).
- **No reservation on accept:** Accepting an offer does **not** lock `Product.reservedCheckoutId`; product reservation still occurs atomically when creating a `CheckoutGroup`.
- **No voucher stacking:** When `CheckoutGroupService.createCheckoutGroup` applies an active accepted offer to any product in the checkout, providing a `voucherCode` is rejected with `400 OFF_010` (`OFFER_VOUCHER_NOT_ALLOWED`).

---

## 2. Data Model (`Offer` entity)

Key fields in `com.moldy.moldymarket.offer.entity.Offer`:
- `id` (`UUID`), `negotiationId` (`UUID`), `previousOfferId` (`UUID`, nullable)
- `productId`, `buyerId`, `sellerId`, `storeId` (nullable for personal listings)
- `originalPrice`, `price` (`BigDecimal`, precision 19, scale 2)
- `side` (`OfferSide`: `BUYER`, `SELLER`)
- `status` (`OfferStatus`: `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `EXPIRED`, `SUPERSEDED`)
- `expiresAt` (`Instant`, response deadline), `acceptedExpiresAt` (`Instant`, checkout deadline)
- `checkoutGroupId` (`UUID`, set when buyer creates checkout using this accepted offer)
- `closeReason` (`String`, e.g. `ACCEPTED_CHECKOUT_EXPIRED`)
- `note` (`String`, max 300 chars, optional message attached to offer/counter)
- `roundNumber` (`int`, `columnDefinition = "integer not null default 1"`, tracks negotiation round `1..3`)
- `version` (`@Version Long`)

---

## 3. REST API Contract (`/api/v1/offers`)

All responses are wrapped in `ApiResponse<T>`. Paginated endpoints return `PageResponse<OfferResponse> { currentPage, totalPages, pageSize, totalElements, data }` (1-based `page`).

| Method | Path | Authority (`@PreAuthorize`) | Request / Params | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/offers` | `OFFER_CREATE` | `{ productId, price, note? }` | Buyer submits initial offer (`201 Created`). |
| `POST` | `/api/v1/offers/{id}/counter` | `OFFER_CREATE` or `OFFER_HANDLE` | `{ price, note? }` | Opposing side supersedes current offer and creates counter (`201 Created`). |
| `PATCH` | `/api/v1/offers/{id}/accept` | `OFFER_CREATE` or `OFFER_HANDLE` | — | Opposing side accepts; sets `acceptedExpiresAt = now + 24h`. |
| `PATCH` | `/api/v1/offers/{id}/reject` | `OFFER_CREATE` or `OFFER_HANDLE` | — | Opposing side rejects pending offer. |
| `PATCH` | `/api/v1/offers/{id}/cancel` | `OFFER_CREATE` | — | Buyer withdraws pending offer. |
| `GET` | `/api/v1/offers/sent` | `OFFER_CREATE` | `?page=1&size=10` | Buyer's offers excluding `SUPERSEDED`, newest first. |
| `GET` | `/api/v1/offers/received` | `OFFER_HANDLE` | `?page=1&size=10` | Store-scoped (via `StoreAccessGuard.findAccessibleStoreId`) or personal seller's received offers excluding `SUPERSEDED`. |
| `GET` | `/api/v1/offers/product/{productId}/active` | `OFFER_CREATE` | — | Returns caller's currently active unexpired `PENDING` or un-checked-out `ACCEPTED` offer for `productId`, or `null`. |
| `GET` | `/api/v1/offers/{id}` | `OFFER_CREATE` or `OFFER_HANDLE` | — | Single offer detail (participants only; store staff with `OFFER_HANDLE` allowed). |
| `GET` | `/api/v1/offers/{id}/history` | `OFFER_CREATE` or `OFFER_HANDLE` | `?page=1&size=10` | Full chronological chain for `offer.negotiationId`. |

### `OfferResponse` DTO
Enriched in batch inside `OfferService` (zero N+1 queries):
```json
{
  "id": "uuid",
  "negotiationId": "uuid",
  "previousOfferId": "uuid | null",
  "productId": "uuid",
  "buyerId": "uuid",
  "sellerId": "uuid",
  "storeId": "uuid | null",
  "originalPrice": 40000000,
  "price": 36000000,
  "side": "BUYER",
  "status": "PENDING",
  "expiresAt": "2026-11-28T08:00:00Z",
  "acceptedExpiresAt": null,
  "createdAt": "2026-11-27T08:00:00Z",
  "updatedAt": "2026-11-27T08:00:00Z",
  "note": "Mình ở gần, lấy trong ngày được ạ",
  "roundNumber": 1,
  "checkoutGroupId": null,
  "closeReason": null,
  "productTitle": "iPhone 16 Pro Max",
  "productImage": "https://...",
  "buyerName": "Test User 1"
}
```

---

## 4. Cross-Module Integrations

1. **Product (`ProductService`, `ProductSearchService`):**
   - `CreateProductRequest` and `UpdateProductRequest` accept optional `Boolean allowOffer` (defaults to `true` on create).
   - `ProductResponse` includes `boolean allowOffer`.
2. **Checkout (`CheckoutGroupService`):**
   - Looks up `findFirstByBuyerIdAndProductIdAndStatusAndCheckoutGroupIdIsNullAndAcceptedExpiresAtAfter(...)` under product pessimistic lock.
   - Uses `offer.getPrice()` as unit price, calls `offer.linkCheckoutGroup(checkoutGroupId, now)`, and blocks vouchers with `OFFER_VOUCHER_NOT_ALLOWED` (`OFF_010`).
3. **Expiration (`OfferExpirationScheduler` -> `OfferService.expirePendingOffers()`):**
   - Expires overdue `PENDING` offers (`expiresAt < now`) AND overdue un-checked-out `ACCEPTED` offers (`checkoutGroupId IS NULL AND acceptedExpiresAt < now`), emitting `OfferEvent(EXPIRED)`.
4. **Notifications (`NotificationEventFactory` & `NotificationEventListener`):**
   - Maps `OfferEvent` to `NotificationType.OFFER_CREATED`, `OFFER_COUNTERED`, `OFFER_ACCEPTED`, `OFFER_REJECTED`, `OFFER_CANCELLED`, `OFFER_EXPIRED`.
5. **Realtime Chat (`OfferChatEventListener`):**
   - On `AFTER_COMMIT`, mirrors `OfferEvent` into the buyer ↔ store-owner conversation as a structured `Message` with `offerId` and `attachedProductId` (keeping `type = TEXT` + human-readable preview for inbox sidebar and PostgreSQL check-constraint compatibility) and broadcasts to both users via STOMP `/user/queue/messages`.

---

## 5. Error Codes

| Code | Enum | HTTP | Meaning |
|---|---|---|---|
| `OFF_001` | `OFFER_NOT_ALLOWED` | 400 | Product does not allow offers (`allowOffer == false`). |
| `OFF_002` | `CANNOT_MAKE_OFFER_FIRST` | 400 | Seller or store member attempted to offer on own product. |
| `OFF_003` | `INVALID_OFFER_PRICE` | 400 | Price outside `[75%, <100%)` or outside narrowing counter bounds. |
| `OFF_004` | `OFFER_LIMIT_REACHED` | 400 | Buyer reached max 3 offers for this product. |
| `OFF_005` | `OFFER_ALREADY_EXISTED` | 400 | Active `PENDING` or `ACCEPTED` offer already exists for `(buyer, product)`. |
| `OFF_006` | `OFFER_NOT_EXISTED` | 404 | Offer not found or caller is not an authorized participant. |
| `OFF_007` | `OFFER_INVALID_STATE` | 400 | Offer state does not allow the requested transition. |
| `OFF_008` | `OFFER_EXPIRED` | 400 | Offer response or checkout deadline has passed. |
| `OFF_009` | `OFFER_NOT_YOUR_TURN` | 400 | Caller tried to respond to their own side's offer. |
| `OFF_010` | `OFFER_VOUCHER_NOT_ALLOWED` | 400 | Voucher code cannot be combined with an accepted offer checkout. |
