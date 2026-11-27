# Moldy Market — Master Codex Onboarding & Handoff Guide

> **Purpose:** Single-read master context for Codex (or any AI coding agent) to immediately understand the entire 3-repository **Moldy Market** platform, its architecture, business invariants, verified API/UI contracts, recent Offer & Chat implementations, known quirks, and how to scan/verify the codebase efficiently.
>
> **Snapshot Date:** `2026-11-27`
> **Project Owner:** Chu (prefers concise Vietnamese replies with English technical terms; English code, JSDoc, comments, and commit messages).

---

## 1. Repository Map & Local Workspace

Moldy Market is a 3-repository C2C/B2C secondhand & authenticatable marketplace with escrow wallet, price negotiation (Offers), real-time Chat, Appraisals, Disputes, and Admin backoffice.

| Repo | Local Path | Remote (`origin/main`) | Last App Code Commit | Dev Port / Proxy |
| :--- | :--- | :--- | :--- | :--- |
| **Backend** | `c:\Do An Tot Nghiep` | `https://github.com/Qt159/Moldy-Market` | `f3ca06a` | `:8080` (Swagger: `/swagger-ui.html`) |
| **User Frontend** | `c:\Do An Tot Nghiep - frontend\Moldy-Market-User-Frontend` | `https://github.com/Chu0701/Moldy-Market-User-Frontend` | `361ea90` | Vite `:5173` (proxies `/api` & SockJS `^/chat/(info\|[0-9]{3}/)` -> `:8080`) |
| **Admin Frontend** | `c:\Do An Tot Nghiep - frontend\Moldy-Market-Admin-Frontend` | `https://github.com/Chu0701/Moldy-Market-Admin-Frontend` | `b285031` | Vite `:5174` / `:5173` (proxies `/api` -> `:8080`) |

### Important Repo Layout Quirks
- **Frontend nested doc directories:** Both Frontend repos have git-tracked docs inside a same-named nested folder:
  - User FE docs: `Moldy-Market-User-Frontend/docs/`
  - User FE agent guide & skills: `user/AGENTS.md` and `user/.agents/skills/`
  - Admin FE docs: `Moldy-Market-Admin-Frontend/docs/`
  - Admin FE agent guide & skills: `admin/AGENTS.md` and `admin/.agents/skills/`
  - Actual React source code lives at `<repo-root>/src/`, `package.json`, `vite.config.js`.
- **Backend untracked local clutter (DO NOT STAGE):** `c:\Do An Tot Nghiep` contains pre-existing untracked files (`.checkout-integration-backup-20261120/`, `.merge-*.log`, `docs/PLANTUML-SEQUENCE-DIAGRAMS.md`, `docs/detailed-design/vbook-1.1.7-win-x64.msi`). **Never run `git add -A` or `git add .` in Backend** — always stage explicit file paths.

---

## 2. Tech Stack & Hard Engineering Rules

### 2.1 Backend (`c:\Do An Tot Nghiep`)
- **Stack:** Spring Boot `4.1.0`, Java `17` target (`pom.xml`), PostgreSQL + Hibernate JPA (`open-in-view: false`), Spring Security + JWT + HttpOnly refresh cookie, Spring WebSocket STOMP (`/chat` with SockJS), AWS S3 (presigned upload/download 5 min), AWS DynamoDB (`notifications` table), MapStruct `1.6.3`, Lombok `1.18.36`, Outbox pattern (`outbox` table), Spotless `2.43.0`.
- **Response Envelope:** All REST endpoints return `ApiResponse<T>`:
  ```json
  { "success": true, "message": "...", "data": { ... }, "errorCode": null, "timestamp": "..." }
  ```
- **Hard JPA / Schema Rules (`ddl-auto: update` on non-empty PostgreSQL DB):**
  1. Whenever adding a new `NOT NULL` column to an existing JPA entity (`Product`, `Offer`, etc.), you **MUST** specify a database-level default in `@Column(nullable = false, columnDefinition = "...")` (e.g., `boolean not null default true`, `integer not null default 1`), otherwise Hibernate fails startup on existing non-empty tables.
  2. Do **NOT** casually add new enum constants to `MessageType` without handling PostgreSQL's `message_type_check` constraint. That is why Offer Chat cards intentionally keep `MessageType.TEXT` + nullable `Message.offerId` rather than adding a new `OFFER` enum value.
- **Authorization Rules:**
  - Use `@PreAuthorize("hasAuthority('...')")` on controller methods.
  - For store-scoped actions (seller/staff), delegate to `StoreAccessGuard` (checks store ownership or active `StoreStaff` membership with required `StorePermission`, e.g., `OFFER_HANDLE`, `CHAT_HANDLE`).

### 2.2 User Frontend & Admin Frontend
- **Stack:** React `19.3.1`, Vite `8.3.5`, Tailwind CSS `4.3.4` (`@tailwindcss/vite`), TanStack Query `5.105.3`, React Router `7.18.5`, Axios `1.21.0`, React Hook Form `7.91.0` + Zod `4.6.7`, Zustand `5.0.16`, i18next `26.7.1` (`vi` default + `en`), Lucide React `1.67.0`, Radix UI / shadcn, Vitest `5.1.0` + Testing Library, ESLint `10`, Prettier `3.9.10`, Node `>=22.12.0`.
- **CSS & Styling Rule (STRICT):**
  - Semantic CSS classes inside `@layer components` in `src/index.css` or feature stylesheets — **avoid giant inline Tailwind utility walls in JSX**.
  - Design aesthetic: Warm neutral luxury secondhand marketplace (`Playfair Display` headings + `Be Vietnam Pro` body, crisp borders, restrained dark primary CTA).
  - **Sticky Header Rule (Regression Prevention):** Never animate or toggle vertical height/padding (`py-*`) on a `sticky top-0` header based on `window.scrollY`! Changing sticky header height shifts document height and causes a scroll oscillation loop ("earthquake" jitter). Keep fixed height (`py-3`) and use hysteresis (`scrollY > 16` / `<= 4`) only for visual decoration (`shadow`/`backdrop-blur`), as implemented in `src/layouts/MainLayout.jsx`.
- **i18n Rule:** Every user-facing string must live in `src/locales/vi/*.json` and `src/locales/en/*.json` (User FE) or `src/locales/` (Admin FE). Keep date/time formats consistent (`HH:mm dd/MM/yyyy` or matching day headers).
- **Formatting Rule:** Prettier checks `.js`, `.jsx`, `.css`, `.json`, **and `.md` files**. Always run `npm run format:check` (or `npm run format`) before committing in either Frontend repo.

---

## 3. Collaboration & Workflow Rules (Owner Preferences)

1. **Language:** Reply to Chu in concise **Vietnamese** (keep technical identifiers/terms in English). Write code, comments, JSDoc, and git commits in **English**.
2. **Clarify Before Guessing:** When UX, architecture, or business rules have meaningful trade-offs, ask Chu structured multiple-choice questions with a `(Recommended)` default option rather than silently assuming.
3. **Git Safety:**
   - Never commit or push unless Chu explicitly asks.
   - Never force-push or amend already-pushed commits.
   - Always verify build/tests/lint before committing.
4. **Windows Environment Notes:**
   - PowerShell 5.1 uses `;` instead of `&&` to chain commands.
   - Backend unit tests run cleanly via `./mvnw.cmd test -Dtest=...` (do not run full DB integration tests unless PostgreSQL + env vars are running).

---

## 4. Module Inventory Across All 3 Repos (As of 2026-11-27)

### 4.1 Backend (`src/main/java/com/moldy/moldyMarket/`) — 29 Packages
All core domain modules are implemented and compiling cleanly:
- **Core Auth & RBAC:** `user`, `role`, `permission`, `userrole`, `security`
- **Store & Catalog:** `store`, `category`, `product`, `pricing`, `wishlist`, `review`
- **Negotiation & Messaging:** `offer`, `chat`, `notification`
- **Commerce & Payments:** `cart`, `order`, `payment`, `voucher`, `commission`, `wallet`, `refund`
- **Trust & Operations:** `appraisal`, `appraiser`, `dispute`, `analytics`
- **Shared / Infra:** `common`, `config`, `controller`, `infrastructure`

### 4.2 User Frontend (`src/features/`) — 21 Implemented Feature Modules
`analytics`, `appraiser`, `auth`, `cart`, `chat`, `checkout`, `disputes`, `marketplace`, `notifications`, `offers`, `orders`, `payments`, `pricing`, `products`, `profile`, `reviews`, `stores`, `system`, `vouchers`, `wallet`, `wishlist`.

All primary buyer & seller routes are implemented, including `/offers`, `/store/offers`, `/chat`, `/chat/:conversationId`, `/products/:id`, `/cart`, `/checkout`, `/orders`, `/wallet`, `/store/*`, `/appraiser/*`.

### 4.3 Admin Frontend (`src/features/`) — 9 Feature Folders
- **Implemented (`b285031`):**
  - Core API layer (`src/api/tokenStorage.js`, `ApiError`, `unwrap`, `client`, `apiErrorMessage`), `@layer components` tokens in `src/index.css`, shared `common/` & `feedback/` UI.
  - Login-free `AdminLayout` shell (`AdminSidebar`, `AdminHeader`) + `dashboard` (`/`).
  - **Group 2 Operations:** `disputes` (`/disputes`, `/disputes/:disputeId`), `withdrawals` (`/withdrawals`), `vouchers` (`/vouchers` platform vouchers).
- **Pending / Placeholder (Next Major Workstream):**
  - `auth` (Admin Login page + route guard — currently shell runs login-free until auth UI is wired).
  - **Group 1 MVP:** `users`, `stores`, `categories`, `products` (approval/moderation queues).
  - Global Admin Order list (Backend currently has no global admin order list endpoint — check `14-BACKEND-INTEGRATION-MATRIX.md` before building).

---

## 5. Key Verified Contracts & Architecture Deep-Dives

### 5.1 Auth, Tokens, Refresh Cookie & Staff
- **Access Token:** Short-lived JWT (`15m`, `900000ms`), sent in `Authorization: Bearer <token>`.
- **Refresh Token:** HttpOnly cookie named `moldy_refresh` (`30d`). Refresh endpoint requires custom header `X-Moldy-Auth: 1` (CSRF mitigation).
- **Storage Namespaces:**
  - User FE: `moldy-market:user:access-token`
  - Admin FE: `moldy-market:admin:access-token`
- **Staff Context:** `GET /api/v1/stores/staff/me` returns the user's active store staff assignments and permissions (drives seller/staff UI gating alongside `store.ownerId === user.id`).
- **Email OTP:** Registration & password reset use real Email OTP (no dev bypass).

### 5.2 Offer Negotiation (Price Bargaining) — End-to-End Contract
Full spec: [`docs/detailed-design/offer-negotiation.md`](docs/detailed-design/offer-negotiation.md).

- **Business Invariants (`application.yaml`):**
  - Price bounds: **75% to <100%** of `product.price` (`offer.min-price-percent: 0.75`, `max-price-percent: 1.00`, strictly `< listedPrice` on initial offer).
  - Max buyer rounds per product: **3** (`offer.max-buyer-offers-per-product: 3`), tracked via `Offer.roundNumber` (`1..3`).
  - Pending response timeout: **24 hours** (`offer.response-hours: 24`).
  - Accepted checkout window: **24 hours** (`offer.accepted-checkout-hours: 24`).
  - Only **1 active (`PENDING` or `ACCEPTED`) offer** per `(buyer, product)` at any time.
  - Product toggle: `Product.allowOffer` (`boolean not null default true`).
  - Turn enforcement: `Offer.supersede(...)` checks turn ownership (`BUYER` cannot counter own pending offer; `SELLER` cannot counter own counter-offer).
  - **No Voucher Stacking:** When checkout uses an accepted offer, applying any store or platform voucher is rejected with `400` `OFF_010` (`OFFER_VOUCHER_NOT_ALLOWED`). Frontend disables voucher inputs when accepted-offer pricing is active.
  - **RBAC:** `@PreAuthorize("hasAuthority('OFFER_CREATE')")` for buyer actions; `@PreAuthorize("hasAuthority('OFFER_HANDLE')")` + `StoreAccessGuard` for seller/staff actions.

- **REST Endpoints (`/api/v1/offers`):**
  1. `POST /api/v1/offers` — Buyer creates offer (`{ productId, offeredPrice, note? }`).
  2. `GET /api/v1/offers/me` — Buyer's offers (paginated, filterable by `status`).
  3. `GET /api/v1/offers/store/{storeId}` — Seller/staff store offers (paginated, filterable by `status`).
  4. `GET /api/v1/offers/product/{productId}/active` — Current buyer's active (`PENDING`/`ACCEPTED`) offer for a product (returns `null` data if none; drives Product Detail CTA & Chat pinned bar).
  5. `GET /api/v1/offers/{offerId}` — Single enriched offer.
  6. `POST /api/v1/offers/{offerId}/accept` — Responding party accepts.
  7. `POST /api/v1/offers/{offerId}/reject` — Responding party rejects.
  8. `POST /api/v1/offers/{offerId}/counter` — Responding party sends counter-offer (`{ counterPrice, note? }`).
  9. `POST /api/v1/offers/{offerId}/cancel` — Initiator cancels pending offer.
  10. `GET /api/v1/offers/product/{productId}/history` — Negotiation thread for `(buyer, product)`.

- **Enriched `OfferResponse` Fields:**
  Includes `id`, `productId`, `productTitle`, `productImage`, `storeId`, `buyerId`, `buyerName`, `listedPrice`, `offeredPrice`, `note`, `roundNumber`, `status`, `turnOf` (`BUYER`/`SELLER`), `expiresAt`, `acceptedExpiresAt`, `checkoutGroupId`, `closeReason`, `createdAt`, `updatedAt`. (Batch-loaded in `OfferService` to avoid N+1 queries.)
- **Error Codes:** `OFF_001`–`OFF_010` (mapped to localized VI/EN messages on User FE).

### 5.3 Real-Time Chat + Structured Offer Cards
Full spec: [`docs/detailed-design/chat.md`](docs/detailed-design/chat.md).

- **Architecture:**
  - REST under `/api/v1/chat/...` + STOMP over SockJS at `/chat` (`/app/chat.send`, user queue `/user/queue/messages`, `/user/queue/read-receipts`).
  - Access control centralized in `ChatAccessPolicy` (buyer vs store owner/staff with `CHAT_HANDLE`).
  - `Message` entity keeps `type = TEXT` and optional `offerId` (`UUID`) & `attachedProductId` (`UUID`).
- **Offer-to-Chat Mirroring (`OfferChatEventListener`):**
  - Offer lifecycle events (`CREATE`, `COUNTER`, `ACCEPT`, `REJECT`, `CANCEL`, `EXPIRE`) emit domain events listened to after commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`).
  - `OfferChatEventListener` ensures a conversation exists between buyer and store, inserts a structured chat `Message` with `offerId` set (no legacy localhost-URL text spam!), and pushes it via STOMP `/user/queue/messages` to both buyer and store participants.
- **User FE Chat UI (`src/features/chat/`):**
  - `ChatOfferCard`: Structured interactive card rendered inline whenever `message.offerId` is present — shows thumbnail + product link, listed (strikethrough) vs offered price + `-X%` badge, optional italic `note`, round `Lượt X/3` with tooltip, contextual status label (`Chờ người bán` / `Chờ bạn phản hồi`), live `OfferCountdown`, inline action buttons (`Đồng ý`, `Trả giá lại`, `Từ chối`, inline-confirmed `Hủy đề nghị`, or prominent `Đặt hàng ngay` on `ACCEPTED`), and dimmed styling for historical/superseded cards.
  - `ChatPinnedOfferBar`: Clean 2-row sticky header inside chat thread (row 1: product title; row 2: offer price + contextual status; right: countdown), clicking scrolls smoothly to `#chat-offer-<id>`.
  - `ChatMessage`: Messenger-style `28px` peer avatar (`ChatAvatar size="xs"`) beside incoming messages, clickable URLs, unified timestamp format, `Đã đọc` shown only for normal messages (omitted on structured offer cards).
  - `ChatComposer`: Includes `BadgeDollarSign` quick Make Offer button when a product context is available.
  - `useChatRealtime.js`: Incoming STOMP message with `offerId` automatically invalidates `offerKeys.all` so cards, pinned bar, and countdowns stay live without refresh.

### 5.4 Cart, Checkout & Payment
- Secondhand marketplace items are **unique items (`quantity = 1`)**.
- Supports **SePay** (bank transfer QR, 15-minute reservation window) and **COD**.
- Checkout supports multi-store grouping (`CheckoutGroupService`) and applies accepted Offer pricing automatically (with savings breakdown, `OfferCountdown` banner, and voucher stacking disabled).

### 5.5 Product Detail Page — 3-Tier `ProductPurchaseBox`
Implemented in `src/features/products/components/ProductPurchaseBox.jsx`, `MobileBuyBar.jsx`, and `ProductDetailPage.jsx`:
- **Price Header:** Prominent price display right inside/above the purchase box.
- **Tier 1 (Primary Purchase):** Full-width dark `Mua ngay` + bordered `Thêm vào giỏ` (`44–48px` height). When an Offer is `ACCEPTED`, `Mua ngay` becomes `Đặt hàng ngay · <acceptedPrice>` with live countdown.
- **Tier 2 (Negotiation):** Full-width bordered `Đưa ra đề nghị giá` (or `Xem đề nghị của bạn` + right-aligned status badge when an offer is active; hidden if `product.allowOffer === false`). Opens `MakeOfferModal` (dot-formatted VND input, 75%–<100% range helper, `−5%` / `−10%` / `−15%` quick chips, instant validation + disabled reason, optional 1-line `note`, 24h escrow note).
- **Tier 3 (Secondary Icon Row):** Compact row (`Yêu thích`, `So sánh`, `Chat` — store chat also lives on Seller Card).
- **Escrow Protection Box:** Concise Moldy Escrow protection block directly below CTAs.
- **Owner/Staff & Sold States:** Owners/store staff see management actions (`Xem đề nghị nhận được`, edit listing) instead of buyer CTAs; sold/hidden items lock purchase buttons and keep wishlist/similar listings.

---

## 6. Known Quirks, Open Decisions & Tech Debt

Keep these in mind before modifying adjacent modules:
1. **`GET /api/v1/orders/me` empty-state behavior:** Historically returned `404` when a user had zero orders — User FE handles this gracefully; verify before refactoring order queries.
2. **Wallet / Escrow `@PreAuthorize` (`B2`):** Verify fine-grained permission annotations on wallet/escrow endpoints when working on financial flows (see `docs/detailed-design/14-BACKEND-INTEGRATION-MATRIX.md`).
3. **Cross-origin CORS + Credentials:** Local dev relies on Vite proxy (`/api` and `^/chat/...`) so cookies (`moldy_refresh`) stay same-origin. If deploying cross-origin, ensure `Access-Control-Allow-Credentials: true` and explicit allowed origins (no `*`) + `SameSite=None; Secure`.
4. **Admin Frontend MVP Gap:** `admin` needs Login/Auth guard (`src/features/auth/`) and Group 1 CRUD/moderation modules (`users`, `stores`, `categories`, `products`) next.
5. **Offer Legacy Text Messages:** The system no longer creates text messages with localhost URLs on offer creation. Any such messages seen in a local DB are old seed/test rows and can be deleted cleanly from the `messages` table.

---

## 7. Recommended Codebase Scan Order for Codex

If you are starting a new task, read in this order to build full mental context in < 2 minutes:

1. **This file** (`c:\Do An Tot Nghiep\CODEX_HANDOFF.md`).
2. **Backend status & contracts:**
   - `c:\Do An Tot Nghiep\docs\BACKEND-CHANGELOG.md` (latest changes)
   - `c:\Do An Tot Nghiep\docs\architecture\architecture-and-progress-summary.md`
   - `c:\Do An Tot Nghiep\docs\detailed-design\14-BACKEND-INTEGRATION-MATRIX.md`
   - Feature-specific specs in `c:\Do An Tot Nghiep\docs\detailed-design\` (especially `offer-negotiation.md`, `chat.md`).
3. **User Frontend rules & routes:**
   - `c:\Do An Tot Nghiep - frontend\Moldy-Market-User-Frontend\user\AGENTS.md`
   - `c:\Do An Tot Nghiep - frontend\Moldy-Market-User-Frontend\Moldy-Market-User-Frontend\docs\04-ROUTING-AND-PAGES.md`
   - `c:\Do An Tot Nghiep - frontend\Moldy-Market-User-Frontend\src\app\router.jsx`
4. **Admin Frontend rules & routes:**
   - `c:\Do An Tot Nghiep - frontend\Moldy-Market-Admin-Frontend\admin\AGENTS.md`
   - `c:\Do An Tot Nghiep - frontend\Moldy-Market-Admin-Frontend\Moldy-Market-Admin-Frontend\docs\HANDOFF-ADMIN-FRONTEND.md`
   - `c:\Do An Tot Nghiep - frontend\Moldy-Market-Admin-Frontend\Moldy-Market-Admin-Frontend\docs\04-ROUTING-AND-PAGES.md`

---

## 8. Fast Verification Commands

### Backend (`c:\Do An Tot Nghiep`)
```powershell
# Compile without running tests
.\mvnw.cmd test-compile

# Run focused unit tests (fast, no PostgreSQL needed)
.\mvnw.cmd test -Dtest=OfferServiceTest,CheckoutGroupServiceTest,OfferChatEventListenerTest

# Run full unit test suite (skip DB-dependent ITs if no local DB)
.\mvnw.cmd test
```

### User Frontend (`c:\Do An Tot Nghiep - frontend\Moldy-Market-User-Frontend`)
```powershell
npm run format:check
npm run lint
npm test
npm run build
```

### Admin Frontend (`c:\Do An Tot Nghiep - frontend\Moldy-Market-Admin-Frontend`)
```powershell
npm run format:check
npm run lint
npm test
npm run build
```
