# Backend change log

This file records Backend changes coordinated with Chu during Frontend integration. Implementation status refers to the local working tree. Test results apply to the named test scope. Git history records committed changes.

## 2026-11-22 — Verified shared-baseline publication candidate

Chu authorized committing and pushing the latest state to main in all three repositories. Backend code/test checkpoint: 25be4d4bf9a91fa918d1cdbe23874349093fc24f. Changes were split into auth/session errors, cart restrictions, reserved checkout/payment/store orders, chat/read receipts, and remaining regression tests. Documentation commits follow that checkpoint without changing application code.

Verification — Auth snapshot 60 tests (one existing disabled QA-010), cart 26/26, commerce/store 140/140, chat 25/25. Final Maven verify/package rebuilt from a fresh snapshot target and passed 314 tests: 313 passed, the same one disabled, zero failures/errors. All 111 candidate source/build files match the verified snapshot. PostgreSQL in Docker was restored after an earlier connection-refused infrastructure failure; no test was disabled to work around it. User Frontend passed 449/449 tests plus format/lint/format check/build; Admin passed those static/build checks and has no behavior test files yet.

Publication excludes environment files, IDE configuration, build output, local backup directories and installers. A configured credential found in the unpublished findings report was redacted before staging; values were never included in the publication report. Older copy-code guides are historical references, not replacements for current application sources.

Business scope — The newly approved Appraiser own-product/owned-store/staff-store prohibition and admin OTP bypass restriction are documented requirements, with Backend enforcement still pending. Appraisal conversationId remains a known placeholder requiring a separate fix. This publication does not implement those new decisions or claim an unrelated security audit is complete. Current cross-repository refs and publication status are maintained in the User Frontend docs/SHARED-BASELINE.md.

## 2026-11-21 — Bounded chat read cursors, unread counts and private receipts

Status — IMPLEMENTED_LOCAL. Chu approved read/unread and delegated direct chat changes. Current contract, schema rollout and verification: docs/testing/CHAT-READ-UNREAD.md.

Change — PATCH conversations/{id}/read requires {messageId}; it advances only the authenticated participant's cursor under the conversation lock. A later incoming message remains unread, older acknowledgements cannot move the cursor backwards, and only committed advances emit private /user/queue/chat-reads events. Conversation responses include both ID cursors and caller-only unreadCount; counts are aggregated in one inbox query, excluding own messages. Legacy timestamp reads remain respected. Additive nullable BIGINT columns are recorded in V20261121__chat_read_cursors.sql; no history is deleted.

Frontend — One session-owned connection at MainLayout updates menu/inbox badges outside chat. Successful visible/focused history acknowledges the observed incoming prefix; failures retain unread state with explicit retry. Peer cursor alone drives outgoing Read labels. Canonical REST refetch/poll repairs missed events; no optimistic read state or automatic send/read retry.

Verification — Full Maven verify/package: 314 tests (313 pass, one existing disabled QA-010), zero failures/errors. Frontend: 449/449 in 49 files and format/lint/format check/build pass. PostgreSQL/HTTP/STOMP tests verify bounded/equal-time IDs, membership, foreign messages, monotonicity, legacy conversion and rollback/no receipt. Frontend tests verify focus/viewport/background behavior, concurrent arrivals, retry/logout and global badge/peer receipt behavior. IntelliJ rerun PID 16268 uses verified classes; categories and SockJS info HTTP 200. UI check remains with Chu. No commit/push.

## 2026-11-21 — Participant names and customer-chat business policy

Status — IMPLEMENTED_LOCAL. Chu authorized direct Backend/frontend edits and delegated the chat business review. Current implementation notes are in docs/testing/CHAT-NAMES-AND-POLICY.md; no further copying is required.

Change — Conversation responses include both participant names and server-computed canSend/sendBlockedReason. Batch lookup replaces per-row name queries, without exposing email/phone. A shared policy rejects self/unavailable participants and current staff-owner customer chat in either direction, while preserving history and permitting other-store contacts. Old staff-owner threads become read-only dynamically; explicit CLOSED state remains final. The prior staff-owner allowance is superseded by this rule. Multiple historical staff rows are handled without dropping relations.

Correctness — Start locks both user rows in stable UUID order, preventing concurrent A-to-B/B-to-A duplicate creation. Legacy duplicate threads are preserved; start reuses the oldest canonical row instead of raising a non-unique-result error. Every message rechecks the same policy. History validates paging and stable ID ties; the read endpoint now updates the actual caller timestamp instead of returning a false success from a stub.

Verification — Full Maven verify/package passed 311 tests: 310 passed, one existing disabled QA-010, zero failures/errors. Unit and real PostgreSQL/HTTP/SockJS/STOMP tests cover names/privacy, membership, inactive/missing/self targets, foreign readers, read timestamps, paging and concurrent creation. Public store ownerId copied by Chu is also verified on the main tree. The Backend was rerun from IntelliJ, preserving its Run/Stop workflow; PID 4484 uses the verified target/classes and categories/SockJS info returned HTTP 200. Frontend passed 437/437 tests in 48 files plus format/lint/format check/build. No schema changes, real-user test messages, commit or push.

## 2026-11-21 — Public store owner ID prepared for chat entry

Status — PREPARED_REVIEWED. Chu requested chat buttons on both product detail and public storefront. The assistant implemented the frontend buttons, but preserved the Backend copy workflow: main PublicStoreResponse has not been changed. Code to copy is in docs/testing/STORE-CHAT-ENTRY.md and store-chat-code/PublicStoreResponse.java.

Contract — Add only UUID ownerId to the public store DTO. Existing StoreMapper maps it by name from Store.getOwnerId(). No new endpoint/controller/schema, private contact details, or invented conversation fields. Both entry points use the existing POST conversations with targetUserId set to the actual owner, including staff-posted listings.

Verification — Missing ownerId reproduced as a failing public HTTP assertion before the patch. Isolated review full Maven verify/package passed 301 tests (300 passed, one pre-existing disabled QA-010, zero failures/errors). The new assertion verifies owner UUID and absence of email/phone/pickupAddress. Reference tests are in docs/testing/store-chat-tests/. The main StoreServiceTest fixture now uses the real mapper instead of a four-argument constructor so it compiles with both old/new DTOs; ID assertion retained and name assertion added. Main focused StoreServiceTest passed. The owner HTTP test stays prepared until Chu copies the DTO.

Frontend — Shared store chat action with participant validation, guest login return URL, owner self-chat guard, staff-to-owner support, single POST lock and no automatic retry. No navigation/cache restoration from late responses after logout/unmount. Missing ownerId leaves the button disabled with a reload action. Full frontend checks passed 429/429 tests in 47 files, format/lint/format check/build. Ten new tests cover both entry points and error/session cases. Storefront tests caught an extra child-mount GET, fixed by reusing the page query. The updated review fixtures passed 14 focused tests; the main compatible fixture passed 8 StoreServiceTest cases. UI/manual integration awaits Chu copying/restarting the Backend. No commit or push.

## 2026-11-21 — Chat TEXT copied, verified and connected to the frontend

Status — IMPLEMENTED_LOCAL after Chu copied the five application files. Corrected copying mistakes in the SEND regex, locked conversation lookup, duplicate save call, messaging validation exception import, and domain error broadcast flag. Removed the obsolete authentication helper/imports. No application files outside the approved chat scope were changed.

Verification — Installed the three prepared test classes under src/test/java/com/moldy/moldymarket/chat. Extended assertions for one save/locked lookup and null message type validation; refreshed the reference tests in docs/testing/chat-realtime-tests/. Final full Maven verify/package passed 300 tests (299 passed, one pre-existing QA-010 disabled, zero failures/errors). Real SockJS/STOMP tests passed with PostgreSQL, including rollback and generic error handling.

Frontend — Added STOMP/SockJS TEXT sending and receiving for existing conversations. Draft clears only on a matching server echo; errors/disconnect/timeout retain it and never automatically resend. Frames and reconnect reload canonical REST history/inbox. Token rotation/logout close the old transport, with callback ownership checks. SockJS proxy paths do not intercept chat SPA routes. Frontend full default test command passed 419/419 in 46 files after bounding Vitest to four workers; format/lint/format check/build passed. Assertions/timeouts were preserved. An earlier parallel Maven/frontend run hit a pre-existing order navigation test timeout, recorded in the frontend learning guide.

Limits — Real UI verification remains with Chu. Product-to-chat entry still needs public store ownerId to respect the decision to message the owner rather than the staff listing creator. Read receipts and attachment sending remain unimplemented. No clientMessageId/idempotency contract was invented; no exactly-once claim. Existing npm audit findings are in the pre-existing shadcn chain, not the added STOMP/SockJS packages. No commit or push.

## 2026-11-21 — Chat TEXT realtime patch prepared for copying

Status — PREPARED_REVIEWED, not installed in the main Backend chat sources. Chu chose to copy Backend code for this task. Full source for five files and copy order are in docs/testing/CHAT-REALTIME-TEXT.md, with exact Java copies in docs/testing/chat-realtime-code/. Tests are prepared in docs/testing/chat-realtime-tests/ for the assistant to install after Chu copies the application files.

Change — Explicit MessageMapper conversation ID mapping; a conversation row lock and transaction covering message insertion/inbox timestamp; participant, ACTIVE conversation and nonblank TEXT checks; generic/validation error replies limited to the originating session; STOMP SEND/SUBSCRIBE destination allowlist with token/account recheck. Existing message destinations and REST APIs remain. Attachment/product mapping and read receipt implementation are outside this patch.

Verification — An isolated copy of the current local Backend working tree reproduced five failures in six initial regression tests. Final full Maven verify/package on the review copy passed 300 tests (299 passed, one pre-existing disabled QA-010, zero failures/errors). Twelve new tests include three real SockJS/STOMP/PostgreSQL server tests: sender/recipient echo identity and persistence, rejection of blank/CLOSED/nonparticipant messages, and rollback plus generic error on mapper failure. Other tests cover scoped destinations, topic membership and expired connected-session tokens. Fixtures clean their own IDs. Main chat application sources and frontend send UI remain unchanged pending copying. No commit or push.

## 2026-11-21 — Staff workspace authorization and product management

Status — IMPLEMENTED_LOCAL. Chu explicitly authorized direct Backend edits for this task and approved delegating PRODUCT_MANAGE_OWN separately from PRODUCT_CREATE. No automatic grants or database permission updates for existing staff.

Cause — users/me merges USER role permissions with delegated staff permissions. USER's ORDER_PROCESS and PRODUCT_MANAGE_OWN do not authorize store resources. The frontend previously used that merged list while StoreAccessGuard correctly required same-store delegated permissions. Staff's self-profile endpoint was also incorrectly protected by owner-only STORE_STAFF_MANAGE.

Change — GET /api/v1/stores/my-store/staff/me now requires STORE_WORKSPACE_ACCESS and returns only the authenticated principal's membership. Staff management endpoints remain owner-protected. New authenticated GET /api/v1/stores/{storeId}/products reuses ProductService.getStoreProducts and StoreAccessGuard; owner or same-store staff with delegated PRODUCT_MANAGE_OWN may list ACTIVE/HIDDEN products. The owner-only my-store/products route remains available. StoreStaffService accepts PRODUCT_MANAGE_OWN in delegated grants.

Verification — Regression tests reproduced the self-profile 403 and missing product route before the fix. Full Maven verify/package passed 288 tests (287 passed, one pre-existing disabled QA-010, zero failures/errors). Five new transactional PostgreSQL/JWT/MockMvc tests verify self-profile isolation, merged-role versus delegated permissions, store isolation, revocation with the same token, blocked stores and anonymous access. The staff permission update unit test also verifies PRODUCT_MANAGE_OWN. Frontend full suite passed 406/406 tests in 44 files; format, lint, format check and build passed. Manual UI verification remains with Chu. Restart the running Backend to load these changes. No commit or push.

## 2026-11-21 — Store order listing implemented locally

Status — IMPLEMENTED_LOCAL after Chu copied the five source files. Chu approved a store order list plus owner COD confirmation UI. Viewing is allowed to the store owner and staff of the same store with ORDER_PROCESS; staff viewing does not grant confirmation. Frontend is now implemented directly by the assistant, while Backend application code remains a copy-and-learn flow. The copied JPQL parameter typo :staus was corrected to :status and the two prepared test files were installed in src/test.

Contract — New GET /api/v1/stores/{storeId}/orders, authenticated, page starts at 1 (default 1), size 1–100 (default 20), optional OrderStatus filter, fixed createdAt DESC/id DESC. Reuse PagedResponse<OrderResponse>, return empty content with HTTP 200. Scope by storeId rather than the listing creator sellerId. Reuse StoreAccessGuard, including STORE_BLOCKED and same-store staff permissions. Existing owner-only COD confirmation and payment lifecycle remain unchanged. OrderResponse has no payment method/status fields; CONFIRMED must not be displayed as proof of COD collection.

Approved adjacent fix — New anonymous HTTP test reproduced a pre-existing security error: CustomAuthenticationEntryPoint used a manually created Jackson 2 mapper that could not serialize ApiResponse.timestamp (Instant). CustomAccessDeniedHandler had the same construction. Chu approved injecting Spring Jackson 3 ObjectMapper via constructors in both handlers, preserving status/code/message.

Verification — Isolated review built from the current dirty integration working tree, not a committed branch or PR: full Maven verify/package passed 283 tests (282 passed, one pre-existing auth QA-010 disabled, zero failures/errors). Nineteen new tests cover service bounds/authorization order, PostgreSQL/JWT/MockMvc store isolation, delegated staff, complete items, stable pagination/filter/empty results, owner-only confirmation and JSON 401/403. Transactional new fixtures roll back. Existing Hibernate 7.4.1.Final supports the fetch-join pagination used here; the integration test enables failure on in-memory collection pagination. No new dependencies, schema changes or disabled tests.

Delivery — Five full source files in docs/testing/STORE-ORDERS-API.md. The prepared test artifacts remain documented in docs/testing/store-orders-tests/ and are now installed in src/test. Full verify/package on the main Backend working tree passed 283 tests: 282 passed and one pre-existing auth test disabled. The assistant directly implemented /store/orders and owner COD confirmation in the User Frontend; all 398 frontend tests, lint, format check and build passed. Manual owner/staff UI and HTTP verification by Chu remains pending. No commit or push.

## 2026-11-20 — Selective integration: online settlement, COD and cancellation

Approved rules — Chu approved completing the selected Backend flow before reviewing the whole result. Online success automatically confirms orders. COD waits for the store owner (including staff-posted products), then admin shipping/delivery/collection. Buyers may cancel unpaid COD before shipping, without refund. Cancelling any unpaid online order cancels the complete shared-QR checkout and preserves the cart. Already-paid online cancellation/refund and escrow remain separate scope.

Settlement — PaymentTransactionService resolves a checkout ID without preloading a stale payment entity and uses the same checkout → payment → sorted product lock order as expiration/cancellation. A valid on-time payment is marked SUCCESSFUL; a synchronous PaymentCompletedEvent listener confirms its stored orders, consumes only the matching product reservations and removes only purchased cart items. The listener requires the existing transaction. Any failure rolls back the receipt, payment, orders, products and cart together. Receipts are unique by provider/external ID; later transfers for an already-paid invoice are recorded as DUPLICATE. Late/cancelled transfers remain LATE for reconciliation and cannot consume a newer hold.

COD — New groups use PAY_ON_DELIVERY, orders PENDING_CONFIRMED. Only the store owner can confirm; buyer detail/cancel checks ownership. Admin action routes are selected from the reviewed branch. Collection requires DELIVERED and COD, sets the order's receipt/completion timestamps, sells its held products and removes only that order's cart items. Repeating collection is idempotent. Group payment remains PENDING during partial collection. COD partial cancellation reduces both stored checkout/payment totals; only remaining collected orders determine final payment success. Unpaid all-cancelled groups close CANCELLED/FAILED. No unpaid COD refund event is emitted.

Contract — Checkout accepts productId-only items by defaulting an omitted quantity to one; explicit quantities above one remain rejected. Payment creation uses the stored checkout buyer/total. PaymentResponse now includes method so clients can determine COD versus online on reload. Explicit mapper bindings populate paymentStatus, checkoutGroupId and orderId, fixing null fields reproduced by contract tests. Fractional VND bank amounts and malformed webhook timestamps return domain errors instead of internal errors. HMAC framing was checked against official SePay documentation; signed HTTP tests reject body tampering and acknowledge duplicate deliveries.

Schema — Existing numeric enum positions are preserved; new states are appended (PENDING_CONFIRMED=9, PAY_ON_DELIVERY=4). The transactional local migration extends check constraints and fixes the payment index column to expired_at. It does not update historical order values. SQL is at src/main/resources/db/migration/V20261120__checkout_statuses.sql; this repository currently has no automatic Flyway runner, so existing databases on other machines need the migration applied explicitly.

Verification — Final full Maven verify/package passed 264 tests (263 passed, one pre-existing disabled QA-010, zero failures/errors), including the additive method DTO contract. Eight PostgreSQL/HTTP tests verify multi-order online settlement, rollback and retry after cart failure, late money, COD progression and admin authorization, two buyers racing for one product, COD partial cancellation persistence, cancelled QR versus a newer hold, and authenticated HTTP webhook retries/tampering. Fixtures are cleaned by their own IDs. A reversed deadline guard was caught by the regression test; source was restored byte-for-byte and all seven webhook service tests passed again. SePay Live transfers and live scheduler timing are not claimed as verified.

Review — Code/learning guide: docs/testing/CHECKOUT-PAYMENT-FLOW.md. Work remains uncommitted on codex/selective-checkout-integration; no push. Frontend submit and payment page are not connected yet. Seller order listing, paid refunds/disputes and escrow were not invented or imported from broken stubs.

## 2026-11-20 — Selective integration: checkout reservations and expiration

Source — Chu approved selective integration from feature/dispute at 4a58390 onto the current main working copy at dc11b09. Work is on codex/selective-checkout-integration. Existing local auth, cart, store, product and ownership changes are preserved; no branch-wide merge or checkout was used. Pre-edit copies of the eight affected existing files are in .checkout-integration-backup-20261120 (local only, do not commit).

Implemented in this first slice — Checkout creation locks product IDs in stable sorted order before reading their status, price and reservation. It rejects an existing active reservation before saving any checkout; after the checkout receives its ID, it reserves every selected product in the same transaction. Online reservations share the checkout's 15-minute deadline; COD reservations have no automatic deadline. The current quantity=1 request contract and COD order status remain unchanged for this slice.

Expiration — The imported scheduler pattern now calls the existing local expire(id, cutoff) method, not the missing method on the source branch. The repository selects UUIDs (c.id), not entities. A 60-second scheduled sweep passes one cutoff to each per-checkout transaction and continues after an individual failure. EnableScheduling is active; checkout.expiration.enabled=false disables the component, and checkout.expiration.interval-ms changes the default interval. The existing expiration service still skips successful payments, COD and progressed orders, preserves the cart, and releases only the matching reservation. Payment expiration remains FAILED under the current local contract.

Product protection — Seller edits, hide and soft deletion reject PRODUCT_RESERVED while an active reservation exists. Product status remains independent from reservation metadata; moderation behavior is preserved.

Verification — The focused red run reproduced six missing-behavior failures. After implementation, compile and 71 scoped cart/product/checkout/reservation/scheduler tests pass with zero skipped tests. Initial full verification lacked the IntelliJ datasource/SePay configuration. Reusing its existing DB/JWT environment and SePay JVM properties only in the Maven process resolved that environment mismatch. Full Maven verify and packaging now pass: 215 tests, zero failures/errors, 214 passed and one previously disabled authentication test (QA-010). The scheduler is disabled only during full tests; its three direct behavior tests still execute. No new test was disabled, no credentials were printed or written into source, and no datasource/IntelliJ configuration was changed. Live lock contention and scheduled execution remain unverified.

Existing schema warning — Payment declares idx_payments_status_expires on expires_at while its entity column is expired_at. Application startup reports this pre-existing index DDL warning but completes and the category HTTP regression passes. Correct it with the payment slice; this slice does not claim payment lifecycle integration is complete.

Remaining integration — PaymentCompletedEvent synchronization, strict late-payment handling, automatic online CONFIRMED/SOLD, paid cart cleanup, cancellation and COD collection are not integrated by this slice. Do not enable the Frontend submit button yet. No dispute/refund stubs from the non-compiling source branch were imported. No commit or push has been made. See docs/testing/CHECKOUT-SELECTIVE-INTEGRATION.md for the actual source and reading guide.

## 2026-11-19 — Checkout validation implemented; reservation pending

Status — Quantity and ownership validation are implemented locally. Chu copied the code; a missed service quantity check was corrected from `<= 0` to `!= 1`. Reservation and payment lifecycle changes remain pending.

- Every secondhand product may be purchased with quantity `1` only.
- A buyer cannot purchase their own product or a product from a store they own or work for. Reuse the store membership guard regardless of staff permissions.
- Online banking reserves the product for 15 minutes. An unpaid expiry or cancellation releases the reservation. COD reserves immediately and releases on cancellation.
- Cart items remain after checkout creation. Remove only the purchased items after confirmed payment; preserve unrelated items and the cart when checkout fails.
- COD collection must also record payment before purchased cart items are removed. The existing `receivedCOD` implementation only changes the order status.

First slice — Request validation and service validation both enforce quantity `1`. Checkout reuses `StoreAccessGuard` to reject the seller and store members before creating a checkout, order or payment, using `CHECKOUT_OWN_PRODUCT` (`ORDER_012`, HTTP 403). Reservation, expiry, cancellation, payment synchronization and cart cleanup remain pending.

Verification — `CheckoutGroupServiceTest`: 10 passing tests; `CartServiceTest`: 21 passing tests. Maven compilation and the scoped test run succeeded. The four new tests failed before implementation. Existing duplicate-product fixture now uses quantity `1` for both repeated entries so it tests duplication separately from quantity validation. HTTP checkout and database concurrency checks remain pending.

Reservation foundation — Chu copied nullable `Product.reservedCheckoutId` and `Product.reservationExpiresAt`, domain reservation/release methods and the pessimistic `ProductRepository.findByIdForUpdate` lookup. Product status stays separate from a temporary reservation. Expired online reservations may be replaced; an old checkout must never clear a newer reservation. Repeating an active reservation for the same checkout must not extend its deadline. COD reservations have no automatic deadline.

Reservation verification — Eight `ProductReservationTest` cases pass after Chu copied the code. Together with 10 checkout service tests and 21 cart service tests, Maven compilation and all 39 scoped tests pass. The initial red run stopped at test compilation because the methods were missing. This foundation is not connected to checkout creation. Release, payment handling, expiration and cancellation must be integrated before checkout starts reserving products. Schema updates, actual database locks and concurrent HTTP checkout requests remain unverified.

Expiration slice — Chu copied `CheckoutExpirationService.expire(checkoutId, now)` and pessimistic checkout/payment repository lookups. An extra `EXPIRED.` in the pending-order comparison was corrected. Lock order is checkout, payment, then product IDs sorted consistently. Only due online-bank checkouts with pending payment and pending-payment orders may expire. Successful payments, COD and progressed orders are preserved. Old expiration must not release another checkout's reservation. Expiration changes group/orders to EXPIRED and payment to FAILED in one transaction; the cart is preserved.

Expiration verification — Seven service tests pass after the copy; together with reservation, checkout and cart tests, all 46 scoped tests and Maven compilation pass. The initial red run failed because the service did not exist. Tests use repository mocks; database lock contention and rollback have not been exercised against a real database. No expiration scheduler or public endpoint is enabled. Checkout creation still does not reserve products. Webhook/payment and cancellation integration remain pending.

Paid cart cleanup — Chu copied internal `CartService.removePurchasedItems(userId, productIds)`. The confirmed-payment flow must supply IDs derived from stored order items. The helper locks the buyer, removes only matching managed cart items, recalculates totals and preserves other items. Missing carts and repeated cleanup do not create a cart or save unchanged data. No HTTP endpoint or automatic cleanup trigger is added.

Cleanup verification — Five new regression tests pass after Chu copied the helper. Cart service now has 26 passing tests. Combined with expiration, product reservation and checkout tests, Maven compilation and all 51 scoped tests pass. The initial red run failed to compile because the helper was missing. Payment-triggered cleanup and database transaction rollback remain unverified because the payment flow is not yet connected.

Q15 decision — On 2026-11-19 Chu chose automatic order confirmation after successful online payment. The planned transaction marks the checkout PAID, its pending-payment orders CONFIRMED and their products SOLD, then removes only the purchased items from the buyer's cart. This is approved behavior, not implemented payment synchronization. Seller approval is not required for paid online orders. Shipping/delivery permissions, cancellation limits and initial COD order status remain separate unresolved decisions.

## 2026-11-18 — Cart integration

Status — First-cart creation, add, update, remove, clear and empty cart response are implemented locally. Cart service tests pass. Frontend integration and real database verification are pending.

- Q7 is resolved. Cart `listingId` refers to `Product.id`.
- Each secondhand product represents one item. Adding the same product again keeps one cart item with quantity `1`.
- Backend obtains the seller and price from the product. The request does not supply either value.
- Product existence and `ACTIVE` status must be checked before adding an item.
- A new cart is associated with its authenticated user. The earlier `user(null)` placeholder has been removed.
- An account without a cart receives an empty cart response when opening the cart page.
- Mutation requests for the same account must be serialized to avoid duplicate active carts and duplicate items on retries or concurrent requests.
- Removing an item updates the managed cart collection and its totals together. Retrying removal of an absent item succeeds with the current cart response.
- Update requests reject quantity above one. Updating an item that is no longer in the cart returns `409 DATA_CONFLICT`.
- `Cart` and `CartItem` have protected no-argument constructors for JPA and private all-argument constructors for their Lombok builders.

Affected code includes `cart/service/CartService.java`, the cart request DTOs, `user/repository/UserRepository.java`, and cart tests. Frontend API documentation will be updated after implementation is verified.

Verification — `CartServiceTest` completed with 21 passing tests. Coverage checks authenticated ownership on first-cart creation, real seller and price, repeated additions, missing and inactive products, default quantity and rejection of quantity above one, an empty response before cart creation, removal totals, repeated removal, clearing absent or populated carts, updates to missing items, and membership restrictions.

Verification pending — HTTP integration, JPA orphan removal and concurrent requests against the real database. Unit tests verify collection totals and lock acquisition with mocks. Summary and seller-group endpoints retain their earlier behavior when no cart exists and are outside this first Frontend flow.

Business rule implemented and tested — Owners and staff cannot add products from their own or linked store to the cart. Legacy personal listings are also blocked when their seller is the buyer. `StoreAccessGuard.isStoreMember` supplies the membership check. Backend returns `403 CART_OWN_PRODUCT` before creating a cart for a prohibited item.

Business decisions still open — Cart-to-checkout cleanup and holding a product during pending payment.

## 2026-11-18 — Resume pending registration and choose the final password

Status — Present in local code, automated checks passed. Latest end-to-end flow awaits confirmation.

- Registering an existing `PENDING_VERIFICATION` email returns `403 EMAIL_NOT_VERIFIED` so Frontend can resume verification.
- Registering an active or locked account returns `EMAIL_ALREADY_EXISTS`.
- Registration retry preserves the existing user and credentials until email ownership is proven.
- `POST /api/v1/auth/verify-email` now receives `{ email, code, password }`.
- Backend validates OTP before encoding and saving the final password, activating the user, and deleting the OTP in the same transaction.
- Active and locked accounts cannot use the verification endpoint to replace their passwords.

Relevant files — `AuthenticationService`, `EmailVerificationService`, `VerifyEmailRequest`, `AuthController`, and their tests.

Verification — `AuthenticationServiceTest`, `EmailVerificationServiceTest`, `AuthControllerTest`, and `UserServiceTest` completed with 44 passing tests and one pre-existing skipped test. Frontend checks completed with 224 passing tests.

## 2026-11-18 — Email OTP activation

Status — Present in local code. Chu confirmed delivery through personal Gmail SMTP.

- Registration creates a `PENDING_VERIFICATION` user and sends an OTP.
- OTP contains six digits, expires after five minutes, and permits five failed attempts per code.
- Resending replaces the old code and resets its attempt count after a minimum interval of 60 seconds.
- OTP hashes and attempt counts are stored in `email_verification_otps`.
- Per-IP and per-email rate limits apply to resend and verification requests.
- Verification and resend lock the user row to serialize operations on the same account.
- Failed verification attempts are committed despite the business exception. SMTP errors roll back registration or the replacement OTP transaction.
- `POST /api/v1/auth/resend-verification` receives `{ email }` and returns a generic success response for missing or non-pending accounts.
- The verification request was subsequently extended to require the final password, as described in the entry above.

Relevant files — `EmailVerificationOtp`, `EmailVerificationOtpRepository`, `EmailVerificationService`, `VerifyEmailRequest`, `ResendVerificationRequest`, `UserRepository`, `AuthenticationService`, `AuthController`, `RateLimitPolicy`, and `ErrorCode`.

Mail configuration — `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH`, and `MAIL_SMTP_STARTTLS` are supplied through the run environment. SMTP connection, read and write timeouts are configured in application properties. Credentials belong in the local run configuration.

Verification — Unit, transaction-proxy and controller tests passed. Transaction-proxy tests verify commit/rollback selection with a mocked transaction manager. Database locking and email delivery still require runtime checks in the configured environment.

## 2026-11-18 — Duplicate phone check during registration

Status — Present in local code and covered by registration tests.

- `AuthenticationService.register()` strips leading and trailing whitespace from a supplied phone number.
- `UserRepository.existsByPhone()` checks for an existing number before user creation and returns `409 PHONE_ALREADY_EXISTS` on conflict.
- Empty optional phone values are stored as `null`.
- The existing registration rate limit remains at the start of registration.

Limitations — This check compares stored strings. Equivalent national and international number formats are not canonicalized. This change does not introduce a database uniqueness constraint, so concurrent registrations still require further database-level protection if strict phone uniqueness is required.

## Earlier integration changes observed on 2026-11-18

Status — Present in the Backend working tree on top of `main` at `dc11b09`. These entries summarize the integration work discussed with Chu. Additional local changes should be reviewed separately.

- Product creation requires a store ID. User Frontend creates listings through a store.
- Product responses expose the version required for optimistic updates.
- Local personal listing endpoint `GET /api/v1/products/me` returns the authenticated seller's personal `ACTIVE` and `HIDDEN` listings.
- Local store listing endpoint `GET /api/v1/stores/my-store/products` resolves the owner's store and lists its `ACTIVE` and `HIDDEN` products with access checks.

Relevant files — `ProductController`, `CreateProductRequest`, `ProductResponse`, `ProductMapper`, `ProductRepository`, `ProductService`, and `StoreController`.
