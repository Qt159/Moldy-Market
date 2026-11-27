# 14 - Backend Integration Matrix (Ma trận tích hợp Backend)

> **Cập nhật 2026-11-27 (v0.5 — Backend `main` `f3ca06a`):**
> Các module trước đây ghi "chưa có API" bên dưới nay **đã triển khai và xác minh trên `main`**:
> - **Offer / Price Negotiation (`IMPLEMENTED` — giải quyết Q8 & B8):** 10 endpoint `/api/v1/offers/**` (`POST /`, `POST /{id}/counter`, `PATCH /{id}/{accept,reject,cancel}`, `GET /sent`, `GET /received`, `GET /product/{productId}/active`, `GET /{id}`, `GET /{id}/history`), phân trang `PageResponse { currentPage, totalPages, pageSize, totalElements, data }`, RBAC `OFFER_CREATE`/`OFFER_HANDLE` + `StoreAccessGuard`, tối thiểu 75% – dưới 100%, tối đa 3 lượt người mua, 24h phản hồi, **24h đặt hàng sau chốt**, chặn cộng dồn voucher (`400 OFF_010` `OFFER_VOUCHER_NOT_ALLOWED`), `Offer.note` & `roundNumber`, batch-enriched `OfferResponse` (`productTitle`, `productImage`, `buyerName`). Chi tiết: `docs/detailed-design/offer-negotiation.md`.
> - **Product `allowOffer` (`IMPLEMENTED`):** `products.allow_offer boolean not null default true`, nhận qua `CreateProductRequest`/`UpdateProductRequest` và trả trong `ProductResponse`.
> - **Chat Offer Card Mirroring (`IMPLEMENTED` — giải quyết Q6 & B8):** `Message.offerId` + `attachedProductId` trong `MessageResponse`; `OfferChatEventListener` tự ghi/cập nhật thẻ Offer trong phòng chat Buyer ↔ Chủ gian hàng và đẩy thời gian thực qua STOMP `/user/queue/messages`. Chi tiết: `docs/detailed-design/chat.md`.
> - **Disputes, Withdrawals, Wishlist, Reviews, Analytics, File Storage, Appraiser/Appraisal:** đã hợp nhất trên `main` và nối sang User Frontend / Admin Frontend (xem `CODEX_HANDOFF.md` & `docs/BACKEND-CHANGELOG.md`).
> - **Vẫn chưa có API:** Follow store, Dashboard analytics hệ thống cho Admin (`ANALYTICS_SYSTEM_VIEW`), danh sách toàn bộ đơn hàng cho Admin.

> Historical snapshot notice — 2026-11-22: the branch/status summary below originated on 2026-11-08 and is not the current shared baseline. For the verified local integration and rollout notes, read docs/BACKEND-CHANGELOG.md and the current frontend integration matrices/handoffs. Exact cross-repository publication checkpoints are recorded in the User Frontend's docs/SHARED-BASELINE.md. Do not infer that newer checkout, chat or appraisal contracts still match these older rows.

> **Trạng thái tài liệu:** v0.4 (cập nhật 2026-11-22: đối chiếu toàn bộ 32 controller trên Backend `main` `07ef520` + nhánh local `codex/selective-checkout-integration` và trạng thái tích hợp của User FE / Admin FE).
> **Áp dụng cho:** Backend, `Moldy-Market-User-Frontend` và `Moldy-Market-Admin-Frontend` (cột "Dùng bởi" cho biết app nào cần mục đó).

Tài liệu này trả lời một câu hỏi: **hiện tại Backend thực sự có gì?** Mọi dòng đều được rút ra từ việc đọc trực tiếp source code Backend.

Nếu một thông tin không có trong tài liệu này, Frontend phải coi nó là **chưa biết** và không được tự bịa ra.

---

## 0. Tóm tắt nhanh (Cập nhật 2026-11-22)

**Đã hợp nhất lên `main` (`07ef520` sau PR #6 `integration/merge-all`) và nhánh local `codex/selective-checkout-integration`:**
- **Auth & OTP:** đăng ký, xác minh email OTP (`/verify-email`, `/resend-verification`), đăng nhập, refresh token (chuyển sang HttpOnly cookie `moldy_refresh` + header `X-Moldy-Auth: 1`), logout.
- **User, Store & Staff:** hồ sơ cá nhân (`/users/me` đã trả `roles`, `permissions`, `storeId`), quản lý gian hàng, sản phẩm cá nhân & gian hàng (`/products/me`, `/stores/my-store/products`, `/stores/{storeId}/products`), quản lý staff (`/stores/my-store/staff/me` dùng `STORE_WORKSPACE_ACCESS`), admin quản lý user & store.
- **Category & Product:** CRUD category, product CRUD (có `version` optimistic lock & reservation giữ chỗ khi checkout), tìm kiếm/gợi ý có rate limit, admin kiểm duyệt product.
- **Cart, Checkout, Order, Store Orders & Payment:** giỏ hàng single-unit (`quantity=1`, chặn tự mua `CART_001`), checkout + reservation 15 phút + tự động hết hạn (`CheckoutExpirationScheduler`), SePay webhook cập nhật đồng bộ `CheckoutGroup`/`Order`/`Product`/Cart (`PaymentCompletedEventListener`), COD (`PAY_ON_DELIVERY`, `PENDING_CONFIRMED`), buyer hủy đơn, owner xác nhận COD, `GET /api/v1/stores/{storeId}/orders`, admin `start-shipping`/`mark-delivered`/`cod-collected`.
- **Chat & Notification:** Chat REST + STOMP/SockJS (`/chat`), chính sách chặn chat staff–owner cùng shop (`CHAT_005`), bounded read cursors (`PATCH /conversations/{id}/read` với `{ messageId }`) + push `/user/queue/chat-reads`, Notification trên DynamoDB (phân trang cursor).
- **Appraiser, Appraisal, Commission, Pricing, Voucher, Wallet/Escrow/Withdrawal & File Storage:** Đã có trên `main`, nhưng **nhiều module chưa qua HTTP/security audit đầy đủ** (đặc biệt Appraisal, Wallet/Escrow/Withdrawal, File Storage).

**Chưa có API:** offer/thương lượng, refund (class rỗng), dispute (mới có entity), wishlist, follow store, đánh giá gian hàng/sản phẩm, thống kê doanh thu gian hàng và thống kê dashboard admin.

**Những điều cần biết trước khi code Frontend:**
1. JWT không chứa role hay permission. `GET /api/v1/users/me` đã trả `roles`, `permissions` và `storeId`; riêng quyền nhân viên trong shop phải lấy từ `GET /api/v1/stores/my-store/staff/me` (không suy từ permissions gộp của `/users/me`).
2. Refresh token là **HttpOnly cookie `moldy_refresh`** do Backend set; `/login`, `/refresh`, `/logout` bắt buộc header `X-Moldy-Auth: 1` và `withCredentials: true`, không gửi/nhận `refreshToken` trong JSON body.
3. Có 4 kiểu phân trang khác nhau (kể cả `List` thuần) và chỉ số `page` lúc bắt đầu từ 1, lúc bắt đầu từ 0 (Chat REST nhận `page` 0-based thủ công; Appraisal/Admin dùng `PaginationConfig` 1-based request nhưng Spring `Page.number` trả 0-based).
4. URL không thống nhất: phần lớn là `/api/v1/...`, nhưng Voucher và Pricing dùng `/api/...` (không có `v1`).
5. `FileController`, `AppraiserCommissionController`, `PaymentTransactionController`, `TestController` trả **RAW JSON (không bọc `ApiResponse`)**.
6. **B2 vẫn mở** trên `WalletController`, `EscrowController`, `WithdrawalController` (chưa có `@PreAuthorize`). `VoucherController`, `ReferencePriceController`, `AutoPricingController`, `AdminAppraiserController`, và các route admin của `OrderController` **đã có `@PreAuthorize` / `@IsAdmin`**.
7. **B10 đã giải quyết bằng Email OTP:** user tự xác minh email OTP để kích hoạt `ACTIVE`. Theo quyết định đã duyệt ngày 2026-11-21, **Admin không được bypass OTP** để kích hoạt `PENDING_VERIFICATION`.
8. **B11 và B12 đã sửa** trên nhánh local `codex/selective-checkout-integration`. **B13 vẫn mở** (`GET /api/v1/orders/me` trả `404 ORDER_003` khi rỗng).

---

## 1. Phương pháp và mốc thời gian

| Mục | Giá trị |
|---|---|
| Repository Backend | `https://github.com/Qt159/Moldy-Market` |
| Ngày cập nhật | 2026-11-22 |
| `main` / nhánh local đã đọc | `main` commit `07ef520` (sau PR #6 `integration/merge-all`) + working tree local nhánh `codex/selective-checkout-integration` |
| Phạm vi kiểm tra | Toàn bộ 32 controllers, 24 packages, `SecurityConfig`, `PaginationConfig`, `JwtAuthenticationFilter`, `ErrorCode`, `StompAuthInterceptor` + đối chiếu User FE & Admin FE |
| Cách xác minh | Đọc trực tiếp source code Backend + đối chiếu các bài test Maven/Vitest đã chạy (xem `docs/BACKEND-CHANGELOG.md`) |

### 1.1 Bảng trạng thái

| Trạng thái | Ý nghĩa trong tài liệu này |
|---|---|
| `IMPLEMENTED` | Code đã có trên `main`, đã đọc controller và DTO. |
| `PARTIALLY_IMPLEMENTED` | Có trên `main`, nhưng một phần hành vi dự kiến còn thiếu hoặc code ghi rõ chưa hỗ trợ. |
| `IN_PROGRESS` | Code chỉ có ở **branch chưa merge**. Tên branch được ghi rõ. Contract có thể thay đổi. |
| `PLANNED` | Có trong tài liệu thiết kế hoặc requirements, nhưng không có code ở branch nào đã đọc. |
| `NOT_FOUND` | Không có code và không có thiết kế dùng được. |
| `REQUIRES_BUSINESS_CLARIFICATION` | Quy tắc nghiệp vụ còn mơ hồ. Team phải quyết định trước khi Frontend xây dựng dựa trên nó. |

Trạng thái tích hợp phía Frontend: `NOT_STARTED` (chưa bắt đầu), `FOUNDATION_ONLY` (đã có công cụ nhưng chưa có service code), `INTEGRATED` (đã tích hợp).
Tại thời điểm viết bản nháp này, **mọi dòng đều là `NOT_STARTED`**.

### 1.2 Quy tắc cho dev Frontend

1. Chỉ các dòng `IMPLEMENTED` mới được viết service function gọi API thật.
2. Dòng `IN_PROGRESS` chỉ được viết service sau khi branch đã merge vào `main` và tài liệu này đã cập nhật.
3. Dòng `PLANNED` và `NOT_FOUND` chỉ làm skeleton trang, đánh dấu `TODO`, không viết code API.

---

## 2. Quy ước chung của Backend (đã xác minh)

### 2.1 Response envelope (khung phản hồi)

Mọi REST response (thành công lẫn lỗi) có dạng:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request completed successfully",
  "data": {},
  "timestamp": "2026-11-03T10:00:00Z"
}
```

Khi lỗi: `success` là `false`, `code` là một chuỗi `ErrorCode` (ví dụ `AUTH_001`), và `data` là object `ApiError` (có ít nhất đường dẫn request, các trường khác chưa được đọc).

Các ngoại lệ đã thấy trong controller:
- Một số endpoint **không có body (`204 No Content`)**: `DELETE /api/v1/categories/{id}`, `DELETE /api/v1/products/{id}`, `DELETE /api/v1/files/{fileId}`, `PATCH /api/v1/conversations/{id}/read`, các thao tác `PATCH .../read`, `DELETE`, `PATCH .../restore` của notification, và `DELETE /api/admin/reference-prices/{id}`. Lớp Axios không được giả định lúc nào cũng có `response.data.data`.
- Các controller trả **RAW JSON (không bọc `ApiResponse`)**: `FileController` (`/api/v1/files/**`), `AppraiserCommissionController` (`/api/v1/appraiser/commissions/**`), `TestController` (`/api/v1/test/protected`).
- Payload WebSocket của chat **không** bọc trong `ApiResponse` (xem mục 5.2).
- Webhook SePay trả `{ "success": true }`, cũng không bọc trong `ApiResponse` (xem mục 5.8). Endpoint này chỉ để SePay gọi, Frontend không bao giờ gọi.

### 2.2 Các kiểu phân trang khác nhau

| Ở đâu | Tham số request | Dạng response | Ghi chú |
|---|---|---|---|
| Danh sách user, store (admin), product cá nhân/shop, store orders | `page` (**bắt đầu từ 1**), `size`, `sort` | `PagedResponse { content, page, size, hasNext, totalElements }` | `totalElements` là `null` khi Backend dùng Slice. `size` tối đa 100, mặc định 20 (`PaginationConfig`). |
| Tìm kiếm product `GET /api/v1/products/search` | `page` (bắt đầu từ 1, dùng cùng resolver), `size` | `ProductSearchResponse { items, page, size, totalElements, totalPages, totalCapped, facets }` | `totalCapped = true` nghĩa là "có từ 10.000 kết quả trở lên". |
| Danh sách product `GET /api/v1/products` | `ProductFilterRequest` + `Pageable` (1-based `page`) | `PagedResponse<ProductResponse>` | Đã xác minh dùng `ProductFilterRequest` (`keyword`, `categoryId`, `minPrice`, `maxPrice`, `condition`) + `Pageable`. Vẫn ưu tiên dùng `/search` cho trang duyệt sản phẩm. |
| Notification | `limit` (mặc định 20, tối đa 50), `token` | `NotificationPageResponse { items, nextToken, pageSize }` | **Phân trang bằng cursor**, token là chuỗi Base64 không đọc được ý nghĩa. `nextToken = null` nghĩa là hết trang. Dùng `useInfiniteQuery` của TanStack Query. |
| Lịch sử chat `GET /api/v1/conversations/{id}/messages` | `page` (**bắt đầu từ 0**, mặc định 0), `size` (mặc định **4**, `1..100`) | Spring `Page<MessageResponse>` do Jackson serialize | Controller nhận `int page` và `int size` thủ công rồi gọi `PageRequest.of(page, size)`, nên bỏ qua resolver 1-based. |
| Danh sách admin pricing/appraisers & các endpoint `appraisal-*` | `Pageable` (request `page` **1-based** theo `PaginationConfig`) | Spring `Page<...>` (`number` **0-based**) | Chú ý bẫy: request `page=1` là trang đầu, nhưng Spring `Page.number` trả `0`. Không dùng `page - 1` như chat. |
| Order và payment của tôi | không có | `List<...>` thuần | `GET /api/v1/orders/me` và `GET /api/v1/payments/my-payments` trả toàn bộ, không phân trang. Riêng `orders/me` vẫn trả **404 `ORDER_003`** khi rỗng; Frontend xử lý fallback về `[]` (B13). |

Frontend cần một adapter riêng cho mỗi dạng (`normalizePagedResponse`, `normalizeProductSearchPage`, `normalizeSpringPage`).

### 2.3 Prefix URL không thống nhất

| Prefix | Module |
|---|---|
| `/api/v1/...` | auth, users, stores, staff, categories, products, notifications, conversations, carts, escrows, wallets, withdrawals, checkout-groups, orders, payments, `payment_transactions` (có dấu gạch dưới), `appraisers`, `admin/appraisers`, `appraisal-requests`, `appraisal-applications`, `appraisal-invitations`, `appraisal-assignments`, `appraisal-reports`, `appraisal-reviews`, `appraiser/commissions`, `files` |
| `/api/...` (không có `v1`) | vouchers (`/api/vouchers`, `/api/admin/vouchers`), pricing (`/api/pricing/auto`), reference prices (`/api/admin/reference-prices`) |

Hệ quả cho Frontend: `baseURL` của Axios **không được gắn cứng `/api/v1`**. Các service dùng nguyên đường dẫn như ghi trong tài liệu này.

### 2.4 CORS

`SecurityConfig` **đã cấu hình CORS** cho `/api/**` (origins từ `${app.cors.allowed-origins}`, methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`, allowedHeaders `Authorization, Content-Type, Accept`, exposedHeaders `Retry-After`, `maxAge=3600`). WebSocket `/chat/**` dùng `setAllowedOriginPatterns("*")`.
**Lưu ý cần bổ sung khi deploy cross-origin:** `CorsConfiguration` hiện **chưa bật `setAllowCredentials(true)`** và **chưa thêm `X-Moldy-Auth` vào `allowedHeaders`**. Khi chạy dev qua Vite proxy (same-origin) hoạt động bình thường; khi deploy khác origin với HttpOnly cookie `moldy_refresh`, Backend cần bổ sung 2 cấu hình này.

### 2.5 Các nhóm mã lỗi (từ `ErrorCode`)

`AUTH_*`, `USER_*`, `ROLE_*`, `STORE_*`, `CAT_*`/`CATEGORY_*`, `PROD_*`, `CART_*`, `ORDER_*`, `PAYMENT_*`, `CHAT_*`, `NOTIFICATION_*`, `PRICING_*`, `VOUCHER_*`, `WALLET_*`, `APPRAISER_*`, `COMMON_*`. Các mã đáng chú ý mà Frontend phải xử lý:

| Mã | HTTP | Ý nghĩa |
|---|---|---|
| `COMMON_001` / `COMMON_002` | 400 | Validation thất bại / Request không hợp lệ |
| `COMMON_003` | 401 | Cần đăng nhập |
| `COMMON_004` | 403 | Không đủ quyền |
| `COMMON_009` | 429 | Gọi quá nhiều (rate limit) |
| `COMMON_010` / `COMMON_011` | 409 | Xung đột dữ liệu / Optimistic lock (`version` ở update product) |
| `AUTH_001` | 401 | Sai email hoặc mật khẩu |
| `AUTH_003` / `AUTH_004` | 403 | Tài khoản bị khóa / bị vô hiệu |
| `AUTH_005` / `AUTH_006` / `AUTH_007` | 401 | Refresh token không hợp lệ / hết hạn / **phát hiện dùng lại** (Backend thu hồi mọi phiên) |
| `AUTH_008` | 403 | Email chưa xác minh OTP (`EMAIL_NOT_VERIFIED`) |
| `AUTH_009` / `AUTH_010` | 400 | Mã OTP sai / hết hạn (`OTP_INVALID` / `OTP_EXPIRED`) |
| `AUTH_011` / `AUTH_012` | 429 | Nhập sai OTP quá số lần / Gửi lại OTP quá nhanh |
| `AUTH_013` | 503 | Gửi email OTP thất bại (`EMAIL_DELIVERY_FAILED`) |
| `STORE_004` / `STORE_015` | 403 | Gian hàng bị chặn / Không có quyền quản lý tài nguyên gian hàng |
| `PROD_008` / `PROD_012` | 409 | Đạt giới hạn số tin đăng / Sản phẩm đang bị giữ chỗ checkout (`PRODUCT_RESERVED`) |
| `PROD_010` / `PROD_011` | 400 | Sản phẩm không còn `ACTIVE` / Sản phẩm không hợp lệ |
| `CART_001` | 403 | Không thể thêm sản phẩm của chính mình / shop mình vào giỏ (`CART_OWN_PRODUCT`) |
| `ORDER_001` / `ORDER_003` | 404 | Order không tồn tại / Danh sách order rỗng (B13) |
| `ORDER_005` / `ORDER_008` | 400 / 409 | Trùng sản phẩm khi checkout / Checkout group không ở trạng thái hợp lệ |
| `ORDER_011` / `ORDER_012` | 403 | Không được truy cập checkout group / Không thể tự mua sản phẩm của mình (`CHECKOUT_OWN_PRODUCT`) |
| `CHAT_005` / `CHAT_006` | 403 | Chặn chat khách hàng giữa staff–owner cùng shop (`CHAT_STORE_MEMBER`) / Participant không khả dụng |
| `PAYMENT_001`..`PAYMENT_003` | 404 / 401 / 400 | Payment không tồn tại / Webhook sai chữ ký hoặc định dạng |

Danh sách đầy đủ nằm trong `ErrorCode.java`. Message của Backend là tiếng Anh; Frontend ánh xạ `code` sang key i18n và không hiển thị trực tiếp message của Backend.

---

## 3. Mô hình xác thực và phân quyền (Cập nhật 2026-11-22)

### 3.1 Endpoint

| Tính năng | Endpoint | Method | Xác thực / Header | Request | `data` trả về |
|---|---|---|---|---|---|
| Đăng ký | `/api/v1/auth/register` | POST | công khai (rate-limited) | `{ email, password(8-100), fullName(<=100), phone? }` | `201 { userId, email, fullName, status }` |
| Xác minh email OTP | `/api/v1/auth/verify-email` | POST | công khai (rate-limited) | `{ email, code(6 digits), password }` | `Void` |
| Gửi lại OTP | `/api/v1/auth/resend-verification` | POST | công khai (rate-limited) | `{ email }` | `Void` |
| Đăng nhập | `/api/v1/auth/login` | POST | công khai + bắt buộc `X-Moldy-Auth: 1` | `{ email(<=254), password }` | `AccessTokenResponse { userId, email, fullName, accessToken, tokenType }` + `Set-Cookie: moldy_refresh` |
| Refresh | `/api/v1/auth/refresh` | POST | Cookie `moldy_refresh` + bắt buộc `X-Moldy-Auth: 1` | không body (`undefined`) | `AccessTokenResponse` + xoay vòng cookie `moldy_refresh` |
| Đăng xuất | `/api/v1/auth/logout` | POST | Cookie `moldy_refresh` + bắt buộc `X-Moldy-Auth: 1` | không body (`undefined`) | `Void` + xóa cookie `moldy_refresh` |

### 3.2 Thông tin về token

| Thông tin | Giá trị | Nguồn |
|---|---|---|
| Access token | Trả trong `AccessTokenResponse.accessToken` (JSON body). Frontend lưu trong `localStorage` qua `src/lib/tokenStorage.js` (`moldy-market:user:access-token` / `moldy-market:admin:access-token`). | `AuthController`, Handoff 2026-11-21 |
| Refresh token | **HttpOnly cookie `moldy_refresh`** (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`). **Không trả trong JSON body.** | `AuthController` |
| Thời hạn access / refresh token | 15 phút (900.000 ms) / 30 ngày (2.592.000.000 ms) | `application-local.yaml` |
| Claim của access token | `sub` (user id), `email`, `tokenType = "ACCESS"` (không chứa role/permission) | `JwtService` |
| Cache-Control & CSRF | `Cache-Control: no-store` trên `/login`, `/refresh`, `/logout`; bắt buộc header `X-Moldy-Auth: 1` | `AuthController` |
| Xoay vòng / phát hiện dùng lại | Mỗi lần refresh thu hồi token cũ và cấp mới. Dùng lại token cũ trả `AUTH_007` và **thu hồi toàn bộ phiên đang hoạt động của user**. | `RefreshTokenService` |

### 3.3 Phân quyền dựa trên permission, tính lại ở mỗi request

`CustomUserDetailsService` dựng danh sách authority ở mỗi request đã xác thực, từ:
1. `ROLE_<tên>` cho từng role (`USER`, `APPRAISER`, `STAFF`, `ADMIN`, `OWNER`).
2. Mọi permission code có grant type `FULL` của các role đó.
3. Nếu user là staff của gian hàng: các permission code **được ủy quyền cho bản ghi staff đó**.

Controller dùng `@PreAuthorize("hasAuthority('<PERMISSION>')")` hoặc `@IsAdmin` / `@IsUser` / `@IsAppraiser`. Entity `UserRole` là `(user_id, role_id, store_id)`.

### 3.4 Frontend biết được gì về permission

| Nhu cầu | Hiện có không? |
|---|---|
| Tên role, permission code và `storeId` của user hiện tại | Có: `GET /api/v1/users/me` trả `roles: string[]`, `permissions: string[]` và `storeId`. |
| Permission được ủy quyền của staff trong gian hàng | Có: `GET /api/v1/stores/my-store/staff/me` (yêu cầu `STORE_WORKSPACE_ACCESS`). **Không dùng `permissions` gộp của `/users/me` để suy ra quyền trên shop** vì role `USER` có sẵn một số permission cá nhân. |

Frontend chỉ dùng `roles`/`permissions` cho UX; Backend luôn xác minh ở mỗi request và trả `403` là quyết định cuối cùng.

### 3.5 Kích hoạt tài khoản sau khi đăng ký (Email OTP — đã triển khai)

1. `POST /api/v1/auth/register` tạo user ở trạng thái `PENDING_VERIFICATION`, gán role `USER`, tạo `Wallet` và gửi mã OTP 6 chữ số qua email (hết hạn 5 phút, tối đa 5 lần thử sai).
2. Đăng ký lại email đang `PENDING_VERIFICATION` trả `403 AUTH_008` (`EMAIL_NOT_VERIFIED`) để Frontend mở lại luồng xác minh.
3. `POST /api/v1/auth/verify-email` kiểm tra OTP, đặt mật khẩu cuối cùng, chuyển user sang `ACTIVE` và xóa OTP trong cùng transaction.
4. **Quyết định nghiệp vụ đã chốt (2026-11-21):** User tự kích hoạt qua email OTP; **Admin không được bypass OTP** để chuyển `PENDING_VERIFICATION` sang `ACTIVE`.

---

## 4. Ma trận tính năng (Cập nhật 2026-11-22)

`Dùng bởi`: U = User Frontend, A = Admin Frontend.

### 4.1 Đã có trên `main` (`07ef520`) và nhánh local `codex/selective-checkout-integration`

| Tính năng | Trạng thái BE | Controller | Endpoint | Xác thực / permission | Dùng bởi | Trạng thái FE |
|---|---|---|---|---|---|---|
| Đăng ký / OTP / đăng nhập / refresh / logout | `IMPLEMENTED` | `AuthController` | xem 3.1 (gồm `/verify-email`, `/resend-verification`) | công khai (`X-Moldy-Auth: 1` + cookie cho login/refresh/logout) | U, A | U: `IMPLEMENTED_LOCAL`; A: `NOT_STARTED` |
| Hồ sơ của tôi / cập nhật / đổi mật khẩu | `IMPLEMENTED` | `UserController` | `GET /api/v1/users/me`, `PUT /me`, `PUT /me/password` | đã đăng nhập (`PROFILE_MANAGE` cho `PUT /me`) | U, A | U: `IMPLEMENTED_LOCAL`; A: `NOT_STARTED` |
| Admin: danh sách / chi tiết / đổi trạng thái user | `IMPLEMENTED` | `UserController` | `GET /api/v1/users`, `GET /{id}`, `PUT /{id}/status` | `USER_STORE_MANAGE` | A | A: `NOT_STARTED` |
| Gian hàng: tạo / xem-sửa my-store / công khai | `IMPLEMENTED` | `StoreController` | `POST /api/v1/stores`, `GET/PUT /my-store`, `GET /{id}` (`PublicStoreResponse` có `ownerId`) | đã đăng nhập / công khai (`GET /{id}`) | U | U: `IMPLEMENTED_LOCAL` |
| Gian hàng: danh sách sản phẩm của shop | `IMPLEMENTED` | `StoreController` | `GET /api/v1/stores/my-store/products`, `GET /api/v1/stores/{storeId}/products` | đã đăng nhập + `StoreAccessGuard` (`PRODUCT_MANAGE_OWN` cho staff) | U | U: `IMPLEMENTED_LOCAL` |
| Admin: danh sách store / đổi trạng thái | `IMPLEMENTED` | `StoreController` | `GET /api/v1/stores`, `PUT /api/v1/stores/{id}/status` | `USER_STORE_MANAGE` | A | A: `NOT_STARTED` |
| Staff: thêm / danh sách / permission / xóa / me | `IMPLEMENTED` | `StoreStaffController` | `POST`, `GET`, `PUT /{staffId}/permissions`, `DELETE /{staffId}` (`STORE_STAFF_MANAGE`); `GET /me` (`STORE_WORKSPACE_ACCESS`) dưới `/api/v1/stores/my-store/staff` | `STORE_STAFF_MANAGE` / `STORE_WORKSPACE_ACCESS` | U | U: `IMPLEMENTED_LOCAL` |
| Category: cây / chi tiết / CRUD admin | `IMPLEMENTED` | `CategoryController` | `GET /api/v1/categories`, `GET /{id}` (công khai); `POST`, `PUT /{id}`, `DELETE /{id}` (`CATEGORY_MANAGE`) | công khai / `CATEGORY_MANAGE` | U, A | U: `INTEGRATED`; A: `NOT_STARTED` |
| Product: CRUD / đổi trạng thái / tin cá nhân | `IMPLEMENTED` | `ProductController` | `POST /api/v1/products`, `PUT /{id}` (có `version`), `DELETE /{id}`, `PATCH /{id}/status?status=`, `GET /api/v1/products/me` | `PRODUCT_CREATE`, `PRODUCT_MANAGE_OWN`, đã đăng nhập (`/me`) | U | U: `IMPLEMENTED_LOCAL` |
| Product: danh sách / chi tiết / search / suggest | `IMPLEMENTED` | `ProductController` | `GET /api/v1/products`, `GET /{id}`, `GET /search`, `GET /suggest?q=` | công khai (rate-limited cho `/search`, `/suggest`) | U, A | U: `IMPLEMENTED_LOCAL` (trừ `/products` list và `/suggest`); A: `NOT_STARTED` |
| Admin kiểm duyệt product | `IMPLEMENTED` | `ProductController` | `PUT /{id}/remove` (`{ reason }`), `PUT /{id}/restore` | `PRODUCT_MODERATE` | A | A: `NOT_STARTED` |
| Giỏ hàng (Cart, qty=1) | `IMPLEMENTED` | `CartController` | `GET /api/v1/carts`, `/summary`, `/by-seller`, `POST /items`, `PATCH/DELETE /items/{listingId}`, `DELETE /api/v1/carts`, `GET /items/{listingId}/exists`, `GET /items/count` | đã đăng nhập (chặn tự mua `CART_001`) | U | U: `IMPLEMENTED_LOCAL` (`GET`, `POST /items`, `DELETE /items/{id}`) |
| Checkout & Payment (SePay + COD + Expiration) | `IMPLEMENTED_LOCAL` | `CheckoutGroupController`, `PaymentController`, `PaymentTransactionController` | `POST/GET /api/v1/checkout-groups`, `GET /api/v1/payments/my-payments`, `GET /{id}`, `POST /api/v1/payment_transactions/webhook/{provider}` | đã đăng nhập (webhook HMAC công khai, RAW) | U | U: `IMPLEMENTED_LOCAL` (`checkout-groups`) |
| Order người mua & gian hàng & admin shipping/COD | `IMPLEMENTED_LOCAL` | `OrderController`, `StoreOrderController` | `GET /api/v1/orders/me` (B13), `GET /{orderId}`, `POST /buyer/{orderId}/cancel`, `POST /seller/{orderId}/confirm`, `GET /api/v1/stores/{storeId}/orders`, Admin (`@IsAdmin`): `PATCH /admin/{id}/start-shipping`, `PATCH /admin/{id}/mark-delivered`, `POST /admin/{id}/cod-collected` | đã đăng nhập / `StoreAccessGuard` / `@IsAdmin` | U, A | U: `IMPLEMENTED_LOCAL`; A: `NOT_STARTED` |
| Chat REST + WebSocket STOMP + Read cursors | `IMPLEMENTED_LOCAL` | `ConversationController`, `ChatController` | `POST/GET /api/v1/conversations`, `GET /{id}/messages` (`page` 0-based), `PATCH /{id}/read` (`{ messageId }`); STOMP `/chat`, `/app/conversations/{id}/send`, `/user/queue/{messages,errors,chat-reads}` | đã đăng nhập + `StompAuthInterceptor` (CONNECT/SEND/SUBSCRIBE) + `ChatAccessPolicy` | U | U: `IMPLEMENTED_LOCAL` |
| Notification (DynamoDB) | `IMPLEMENTED` (chưa audit HTTP FE) | `NotificationController` | `GET /api/v1/notifications?limit&token`, `GET /{id}`, `PATCH /{id}/read`, `DELETE /{id}`, `PATCH /{id}/restore` | đã đăng nhập | U | U: `NOT_STARTED` |

### 4.2 Đã merge trên `main` nhưng cần Audit quyền/DTO trước khi nối FE

| Tính năng | Controller | Endpoint | Xác thực quan sát được & Lưu ý | Dùng bởi | FE |
|---|---|---|---|---|---|
| Ví / Escrow / Rút tiền | `WalletController`, `EscrowController`, `WithdrawalController` | `GET /api/v1/wallets/{userId}/balance`; `POST /api/v1/escrows/{hold,orders/{id}/capture,orders/{id}/release}`; `POST /api/v1/withdrawals`, `PATCH /{id}/{approve,complete,reject,fail}` | **B2 MỞ:** chỉ yêu cầu `authenticated()`, chưa có `@PreAuthorize` | U, A | `NOT_STARTED` |
| Voucher | `VoucherController` | `GET /api/vouchers/{code}` (hiện cần đăng nhập — không có trong `permitAll`); `POST/GET /api/admin/vouchers`, `GET /api/admin/vouchers/{code}` | Admin có `@IsAdmin` | U, A | `NOT_STARTED` |
| Auto Pricing & Reference Price | `AutoPricingController`, `ReferencePriceController` | `POST /api/pricing/auto` (`@IsUser`); CRUD `/api/admin/reference-prices` (`@IsAdmin`, Spring `Page`) | Đã có `@IsUser` / `@IsAdmin` | U, A | `NOT_STARTED` |
| Hồ sơ Appraiser (User & Admin) | `AppraiserController`, `AdminAppraiserController` | `/api/v1/appraisers/{register,me,me/status,active,{id},me/deactivate,me/reactivate}`; `/api/v1/admin/appraisers` (`hasRole('ADMIN')`: list, `pending`, `/{id}/status`, `approve`, `reject`, `suspend`, `resume`) | Cần HTTP audit; `active` & `{id}` hiện chưa nằm trong `permitAll` | U, A | `NOT_STARTED` |
| Quy trình thẩm định (Appraisal 6 bước) | `AppraisalRequest*`, `AppraiserApplication*`, `AppraisalInvitation*`, `AppraisalAssignment*`, `AppraisalReport*`, `AppraisalReview*` | `/api/v1/appraisal-requests/**`, `/api/v1/appraisal-applications/**`, `/api/v1/appraisal-invitations/**`, `/api/v1/appraisal-assignments/**`, `/api/v1/appraisal-reports/**`, `/api/v1/appraisal-reviews/**` | **Cần HTTP/security audit** (xem `HANDOFF-APPRAISER-FRONTEND.md`); `AppraisalConversationService` còn placeholder `UUID.randomUUID()` | U | `NOT_STARTED` |
| Hoa hồng Appraiser | `AppraiserCommissionController` | `GET /api/v1/appraiser/commissions/my`, `GET /{commissionId}` | Đã đăng nhập; trả **RAW JSON** (không bọc `ApiResponse`) | U | `NOT_STARTED` |
| File Storage (Presigned URL) | `FileController` | `POST /api/v1/files/presigned-url`, `POST /{fileId}/confirm`, `GET /{fileId}/download-url`, `DELETE /{fileId}` | Đã đăng nhập; trả **RAW JSON** (không bọc `ApiResponse`) | U, A | `NOT_STARTED` |

### 4.3 Chưa có API (`NOT_FOUND` / `PLANNED`)

| Tính năng | Trạng thái | Bằng chứng |
|---|---|---|
| Quy trình **mời** staff | `PLANNED` | Chỉ có thêm trực tiếp bằng email |
| Offer / thương lượng / counteroffer | `NOT_FOUND` | Không có code offer |
| Yêu cầu và duyệt refund / dispute | `NOT_FOUND` / `REQUIRES_BUSINESS_CLARIFICATION` | `Refund` rỗng, `DisputeRequest` chỉ có entity, chưa có controller/service |
| Wishlist / Follow gian hàng / Đánh giá shop | `NOT_FOUND` | Không có controller |
| Thống kê doanh thu shop / Dashboard admin | `NOT_FOUND` | Không có controller |
| Notification realtime push | `NOT_FOUND` | Mới có DynamoDB + SQS publisher, chưa có kênh đẩy xuống trình duyệt |
| Cập nhật trạng thái order và checkout group sau khi thanh toán thành công, và tự động hết hạn | `NOT_FOUND` | Chỉ `Payment` được cập nhật. `PaymentCompletedEvent` được phát nhưng không có listener nào. `CheckoutGroup.markPaid()` và `markExpired()` có định nghĩa nhưng không ai gọi. Không thấy job `@Scheduled` nào. Xem B12. |
| Offer / thương lượng / counteroffer | `NOT_FOUND` | Không có code offer. `offer-negotiation.md` rỗng. Giới hạn số vòng chưa biết. |
| Yêu cầu và duyệt refund | `NOT_FOUND` dưới dạng API. Class `Refund` (trên `feature/order`) hiện **rỗng**, không có trường nào. Module escrow có `DisputeOutcome { PAY_SELLER, REFUND_BUYER }` và trạng thái `DISPUTED`, nhưng chưa controller nào mở refund hay dispute ra ngoài. | Xem các endpoint escrow ở 4.2. |
| Quy trình return / dispute | `REQUIRES_BUSINESS_CLARIFICATION` | Có permission `DISPUTE_*`. Entity `DisputeRequest` (trên `feature/order`) có `userId`, `orderId`, `description`, `imagesUrl`, `createdAt` nhưng chưa có controller hay service. Tài liệu thiết kế mô tả các trạng thái `OPEN`, `UNDER_REVIEW`, `RESOLVED_REFUND`, `RESOLVED_RELEASE`, `CLOSED` nhưng chưa được triển khai. |
| Wishlist | `NOT_FOUND` | không có code |
| Follow gian hàng | `NOT_FOUND` | không có code |
| Đánh giá gian hàng / người bán | `NOT_FOUND` | có permission `REVIEW_CREATE`, không có code |
| Tổng quan doanh thu / giao dịch của gian hàng | `NOT_FOUND` | có permission `STORE_STATS_VIEW`, không có controller |
| Thống kê dashboard admin | `NOT_FOUND` | có permission `ANALYTICS_SYSTEM_VIEW`, không có controller |
| Admin: quản lý giao dịch, khiếu nại, báo cáo/vi phạm, cấu hình hệ thống | `NOT_FOUND` | không có controller |
| Biểu đồ giá / phân tích thị trường | `NOT_FOUND` | Reference price chỉ có CRUD cho admin (4.2). Không endpoint nào trả giá trung bình theo category hay sản phẩm tương tự. |
| Sản phẩm tương tự | `NOT_FOUND` | không có endpoint riêng (endpoint search có thể đáp ứng một phần, cần hỏi team) |
| Upload ảnh | `NOT_FOUND` | `image-upload.md` rỗng. Ảnh product được gửi dưới dạng **URL HTTPS** (tối đa 10, mỗi URL tối đa 500 ký tự). Cơ chế upload chưa rõ. Xem Q5. |
| Notification thời gian thực | `NOT_FOUND` | Notification lưu trong DynamoDB và phát qua SQS publisher. Không thấy kênh đẩy xuống trình duyệt. |

---

## 5. Contract đã xác minh của các module đang phát triển

> Các contract này lấy từ branch chưa merge. Hãy coi là **tạm thời**.

### 5.1 Notification

```text
NotificationRecord {
  notificationId: string, userId: string, type: string,
  referenceType: string, referenceId: string,
  title: string, content: string, isRead: boolean,
  createdAt: string, deletedAt: string | null
}
NotificationPageResponse { items: NotificationRecord[], nextToken: string | null, pageSize: number }
```

- `type` và `referenceType` là chuỗi thường. **Không có enum liệt kê giá trị trong code**, nên Frontend phải có cách hiển thị dự phòng cho type chưa biết.
- Trường cờ đã đọc có thể được serialize thành `read` hoặc `isRead` tùy cấu hình Jackson với record component tên `isRead`. Phải kiểm tra bằng response thật trước khi code.
- Các thao tác `PATCH` và `DELETE` trả body rỗng.
- ID của notification là chuỗi (không phải kiểu UUID).

### 5.2 Chat và WebSocket

| Mục | Giá trị đã xác minh |
|---|---|
| Giao thức | STOMP qua SockJS |
| Endpoint kết nối | `/chat` (nằm trên origin của Backend; SockJS tự thêm các sub-path của nó) |
| Xác thực | Frame STOMP `CONNECT` phải có native header `Authorization: Bearer <accessToken>`. Thiếu hoặc token sai sẽ bị từ chối kết nối. |
| Destination để gửi | `/app/conversations/{conversationId}/send` |
| Payload gửi | `{ type: "TEXT" \| "IMAGE" \| "FILE", content?: string, attachmentUrl?: string }` |
| Destination để nhận | `/user/queue/messages` (server đẩy cho cả người gửi lẫn người nhận) |
| Payload nhận | `MessageResponse { id: number, conversationId, senderId, type, content, attachment, sentAt }` |
| Broker prefix | `/topic`, `/queue`; application prefix `/app`; user prefix `/user` |
| Kênh báo lỗi | `/user/queue/errors`. Khi gửi tin gặp `AppException`, server đẩy `ChatErrorResponse { errorType, errorMessage, timestamp }` (tham số đầu là mã `ErrorCode`). Các exception khác `AppException` chưa có handler. Bản cũ ở `feature/cart` và `integration/demo` chưa có kênh này. |
| Subscribe | Khi SUBSCRIBE vào `/topic/conversations/{uuid}`, Backend kiểm tra user có thuộc conversation không. Tuy nhiên tin nhắn hiện được đẩy qua `/user/queue/messages`, không qua topic này. Frontend không cần subscribe topic. |
| Token hết hạn | Token chỉ được kiểm tra lúc CONNECT. Chưa biết chuyện gì xảy ra khi access token (15 phút) hết hạn trên socket đang mở. |

Phần REST:

| Endpoint | Request | `data` trả về |
|---|---|---|
| `POST /api/v1/conversations` | `{ targetUserId }` | `ConversationResponse { id, participant1Id, participant2Id, status(ACTIVE\|CLOSED), lastMessageAt, lastReadAtParticipant1, lastReadAtParticipant2, createdAt }` (tạo mới hoặc trả lại cuộc hội thoại có sẵn) |
| `GET /api/v1/conversations` | không | `ConversationResponse[]` (không phân trang) |
| `GET /api/v1/conversations/{id}/messages?page&size` | xem 2.2 | `Page<MessageResponse>`, tin mới nhất trước |
| `PATCH /api/v1/conversations/{id}/read` | không | không có data (đánh dấu đã đọc), chỉ có ở bản `feature/chat` mới nhất và `feature/order` |

Lưu ý quan trọng: một conversation chỉ là giữa **hai user** và được tạo chỉ với `targetUserId`. **Không có liên kết tới offer, order hay product** trong bất kỳ DTO nào. Điều này khác với ý tưởng chat gắn với offer. Xem Q6.
Endpoint "đánh dấu đã đọc" chỉ có ở bản `feature/chat` mới nhất.

### 5.3 Cart

`CartResponse { id, userId, status, items[], subtotal, totalItems, createdAt, lastUpdatedAt, abandonedAt }` với `CartItemResponse { id, listingId, sellerId, quantity, priceAtAddTime, totalPrice, note, createdAt, updatedAt }`.
Request: thêm `{ listingId, quantity >= 1, note }`, cập nhật `{ quantity >= 1, note }`.
Backend gọi product là `listingId`, trong khi `ProductResponse` dùng trường `id`. Xem Q7. Các giá trị của `CartStatus` chưa audit.

### 5.4 Escrow và withdrawal (chỉ mô hình dữ liệu)

- `EscrowStatus`: `HELD`, `CAPTURED`, `RELEASED`, `DISPUTED`.
- `DisputeOutcome`: `PAY_SELLER`, `REFUND_BUYER`.
- `WithdrawalStatus`: `REQUESTED`, `PROCESSING`, `COMPLETED`, `FAILED`, `REJECTED`.
- `EscrowHoldRequest { orderId: string, buyerId, sellerId, amount, fee }`; `EscrowHoldResponse { id: number, orderId: number, amount, feeAmount, status, createdAt, resolvedAt }` (lưu ý `orderId` là chuỗi ở request nhưng là số ở response).
- Các endpoint này trông giống **thao tác nội bộ của service** (client có thể gọi `hold` với buyer và số tiền tùy ý). Frontend có nên gọi chúng hay không là câu hỏi nghiệp vụ và bảo mật, xem B2 và Q4.

### 5.5 Voucher

`VoucherCheckResponse { code, discountType, discountValue, expiredAt, valid }`; `VoucherResponse { code, discountType, discountValue, remainingQuantity, expiredAt }`; request tạo `{ code, discountType, discountValue > 0, remainingQuantity > 0, expiredAt }`.
Hiện chỉ có API voucher cho **hệ thống/admin**. Chưa có API voucher cho người bán, dù đã có permission `VOUCHER_SELLER_MANAGE`. Các giá trị của `DiscountType` chưa audit.

### 5.6 Điểm chính của contract Product (`main`)

- Tạo product bắt buộc có `categoryId`, `title (<=150)`, `price (0 < price <= 1.000.000.000, tối đa 2 chữ số thập phân)`, `condition`, `pricingStrategy`. Tùy chọn: `storeId` (null nghĩa là đăng bán cá nhân), `description (<=2000)`, `images (<=10, chỉ https)`, `attributes` (kiểm tra bằng `@ValidProductAttributes`).
- Cập nhật bắt buộc có `version` (optimistic locking). Version cũ sẽ trả `COMMON_010`.
- Enum: `ProductCondition { NEW, LIKE_NEW, USED, DAMAGED }`, `ProductStatus { ACTIVE, HIDDEN, SOLD, DELETED, REMOVED }`, `PricingStrategy { SELLER_SET, AUTO_PRICING, APPRAISER }`.
- Người bán chỉ được chuyển qua lại giữa `ACTIVE` và `HIDDEN` (`PROD_004`).
- Tham số search: `q (<=100)`, `categoryId[] (<=20)`, `condition[] (<=10)`, `minPrice`, `maxPrice`, `storeId`, `sellerType`, `attr[] (<=10)`, `sort`, `facets`. Giá trị `sort` hợp lệ: `relevance`, `newest`, `price_asc`, `price_desc`. `attr` được parse thành khóa và danh sách giá trị (OR trong cùng một khóa); định dạng chuỗi chính xác chưa audit.
- Các enum khác đã xác minh: `UserStatus { ACTIVE, LOCKED, PENDING_VERIFICATION }`, `StoreStatus { ACTIVE, BLOCKED }`.

### 5.7 Checkout và Order (`feature/order`, chưa merge)

> Contract tạm thời. Nội dung dưới đây lấy từ `CheckoutGroupController`, `OrderController`, các DTO và `CheckoutGroupService`.

**Tạo checkout:** `POST /api/v1/checkout-groups`

> Lưu ý: team đã quyết định bỏ `quantity` (mỗi sản phẩm cũ chỉ bán 1 món, xem Q17). Request dưới đây là bản **hiện tại trên branch** và sẽ thay đổi khi Backend sửa.

```text
CheckoutGroupRequest {
  orderItems: [{ productId: UUID, quantity: 1..100 }]   // 1 đến 20 phần tử
  receiverName: string (<=255)
  phoneNumber: string, theo regex ^(\+84|0)\d{9,10}$
  shippingAddress: string (<=500)
  method: "COD" | "ONLINE_BANKING"
  note?: string (<=500)
}
```

Quy tắc đã xác minh trong code:
- **Giá do Backend tính** từ `product.price`. Frontend không gửi giá và không được tự tính tổng để quyết định số tiền thanh toán; chỉ hiển thị `totalAmount` do Backend trả.
- Không được trùng `productId` trong một request (lỗi `ORDER_005`). Mọi product phải tồn tại và đang `ACTIVE` (lỗi `PROD_010` nếu không).
- Backend **tự tách thành nhiều order** theo cặp `(sellerId, storeId)`. Một lần checkout có thể trả về nhiều order.
- `ONLINE_BANKING`: `expiredAt = bây giờ + 15 phút`. `COD`: `expiredAt = null`.
- Phí ship luôn là 0. Voucher chưa được áp dụng (phần code bị comment). Tồn kho chưa được giữ hoặc trừ (`//todo: subtract quantity, reverse stock`). Xem B14.

```text
CheckoutResponse {
  id: UUID, totalAmount, expiredAt | null,
  orders: OrderSummaryResponse[], status: CheckoutGroupStatus,
  paymentResponse: PaymentResponse
}
OrderSummaryResponse { id, orderCode, checkoutGroupId, buyerId, sellerId, status, totalAmount, createdAt, expiredAt, items[] }
PaymentResponse { id, amount, paymentStatus: string, qrBankUrl: string | null, checkoutGroupId }
```

Đọc lại: `GET /api/v1/checkout-groups/{id}` trả cùng `CheckoutResponse`. Chỉ người mua được xem (`ORDER_011` nếu không phải).

**Order của người mua:**

```text
OrderResponse {
  id, orderCode, checkoutGroupId, buyerId, sellerId, storeId, status,
  totalAmount, receiverName, receiverPhone, shippingFee, shippingAddress,
  shopDiscount, platformDiscount,
  confirmedAt, shippedAt, deliveredAt, returnDeadline, completedAt, cancelledAt, createdAt,
  items: [{ id: number, orderId, productId, productName, unitPrice, quantity, imageUrl }]
}
```

- `GET /api/v1/orders/me` trả `OrderResponse[]`, không phân trang, và trả **404 `ORDER_003`** khi rỗng (B13).
- `GET /api/v1/orders/{orderId}` **không kiểm tra chủ sở hữu** (B11).
- `POST /api/v1/orders/admin/{orderId}/cod-collected` (`@IsAdmin`): chỉ cho order đang `DELIVERED`, chuyển sang `COMPLETED`; order ở trạng thái khác trả `ORDER_009`.

**Enum (đã xác minh):**
- `CheckoutGroupStatus`: `PENDING_PAYMENT`, `PAID`, `CANCELLED`, `EXPIRED`.
- `OrderStatus`: `PENDING_PAYMENT`, `CONFIRMED`, `SHIPPING`, `DELIVERED`, `COMPLETED`, `CANCELLED`, `EXPIRED`, `DISPUTED`, `REFUNDED`.
- `PaymentMethod`: `COD`, `ONLINE_BANKING`.
- `PaymentStatus`: `PENDING`, `PROCESSING`, `SUCCESSFUL`, `FAILED`.

**Chuyển trạng thái order thực sự có trong code:** order được tạo ở `PENDING_PAYMENT`; `DELIVERED` sang `COMPLETED` do admin. Mọi chuyển trạng thái khác chưa có code, kể cả các trạng thái có trong enum. Frontend không được vẽ timeline order như thể các bước này đã chạy.

### 5.8 Thanh toán SePay (`feature/order`, chưa merge)

**Phía Frontend nhận được gì:** trong `PaymentResponse`, `qrBankUrl` là một URL ảnh QR do Backend tạo (dạng `https://vietqr.app/img?acc=...&bank=...&amount=...&des=<mã thanh toán>`), và là `null` với `COD`. Mã thanh toán có dạng `MM` + 8 ký tự chữ hoa hoặc số, được dùng làm nội dung chuyển khoản. Số tài khoản và tên ngân hàng nằm trong cấu hình Backend. Frontend chỉ hiển thị ảnh theo URL này và **không tự dựng QR**. Chưa có endpoint nào trả thông tin chuyển khoản dạng chữ (số tài khoản, ngân hàng, nội dung), xem Q11.

**Luồng webhook (chỉ để hiểu, Frontend không gọi):**

1. SePay gọi `POST /api/v1/payment_transactions/webhook/sepay`. Endpoint công khai, nhưng Backend kiểm tra chữ ký HMAC-SHA256 trong header `X-SePay-Signature` (dạng `sha256=<hex>`, tính trên `timestamp + "." + body`) cùng `X-SePay-TimeStamp` lệch không quá 300 giây. Sai thì trả `PAYMENT_002`.
2. Mỗi giao dịch được lưu theo `(provider, externalId)`, nên SePay gửi lại cùng giao dịch sẽ bị bỏ qua (idempotent).
3. Giao dịch tiền ra (`OUT`) chỉ ghi nhận (`IGNORED`).
4. Backend tìm payment theo mã `MM[A-Z0-9]{8}` trong nội dung chuyển khoản. Không tìm thấy thì ghi `UNMATCHED`.
5. **Số tiền phải khớp chính xác** với `payment.amount`. Lệch thì ghi `AMOUNT_MISMATCH` và payment vẫn `PENDING`.
6. Khớp và payment đang `PENDING`: payment chuyển `SUCCESSFUL`, ghi `paidAt`, phát `PaymentCompletedEvent`. Nếu payment không còn `PENDING`: ghi `DUPLICATE` (đã thành công) hoặc `LATE` (đã hết hạn hay thất bại).
7. `MatchStatus`: `MATCHED`, `UNMATCHED`, `AMOUNT_MISMATCH`, `LATE`, `DUPLICATE`, `IGNORED`.

**Hệ quả cho Frontend:**

- Không có kênh push. Để biết đã thanh toán, Frontend phải **polling** `GET /api/v1/payments/{id}` (hoặc `GET /api/v1/checkout-groups/{id}`) và chỉ coi là thành công khi `paymentStatus === "SUCCESSFUL"` do Backend trả. Bấm nút "Tôi đã chuyển khoản" không bao giờ được coi là thanh toán thành công.
- Chỉ tin `paymentStatus`. `CheckoutGroup.status` và `Order.status` hiện **không** được cập nhật sau khi thanh toán (B12), nên chúng vẫn là `PENDING_PAYMENT` dù tiền đã về.
- `expiredAt` chỉ để hiển thị đếm ngược. Không thấy job tự động chuyển trạng thái hết hạn, nên đếm ngược về 0 không có nghĩa Backend đã huỷ. Chuyển khoản trễ hoặc lệch số tiền chưa có đường xử lý (code ghi `//todo: late/duplicate -> refund or re-reserve`).
- `paymentStatus` trong DTO là chuỗi, giá trị theo enum `PaymentStatus`. Hãy xử lý giá trị lạ bằng nhánh mặc định.

---

## 6. Vấn đề cần báo team Backend

Đây là những điều quan sát được khi đọc code, không phải việc của Frontend.

| ID | Quan sát | Vì sao Frontend cần quan tâm |
|---|---|---|
| B1 | Không có cấu hình CORS cho REST. | Trình duyệt không gọi được API khác origin, trừ khi đi qua Vite proxy. |
| B2 | Trên `feature/cart` (và các feature branch khác), **không** controller nào của cart, wallet, escrow, withdrawal, voucher admin, pricing, reference-price hay appraiser admin có `@PreAuthorize`, và `SecurityConfig` chỉ yêu cầu `authenticated()`. Bất kỳ user nào đã đăng nhập đều có thể gọi `/api/admin/**`, `/api/v1/escrows/**` và `/api/v1/withdrawals/*/approve`. | Frontend có ẩn giao diện admin thì cũng không phải là bảo mật. Backend cần sửa trước khi phát hành. |
| B3 | Các branch lệch nhau: `SecurityConfig` của `feature/cart` cho phép công khai `GET /api/categories/**` và `GET /api/vouchers/**` nhưng không liệt kê `/api/v1/products...` hay `/api/v1/categories`, trong khi `main` thì ngược lại. | Sau khi merge, việc duyệt công khai có thể âm thầm bắt phải đăng nhập. Cần kiểm tra lại danh sách route công khai sau khi tích hợp. `SecurityConfig` của `feature/order` (đã gộp `main`) liệt kê đủ route công khai của product, category, store, `/chat/**` và webhook, nên có thể dùng làm mốc đối chiếu. |
| B4 | Prefix URL lẫn lộn (`/api/v1` và `/api`). | Xem 2.3. |
| B5 | Đã được xử lý ở `feature/chat` mới nhất: lỗi `AppException` được gửi tới `/user/queue/errors`. Các branch cũ hơn (`cart`, `integration/demo`) vẫn chưa có. Lỗi không phải `AppException` vẫn chưa có đường báo. | Cần chốt branch tích hợp để biết dùng bản nào. |
| B6 | Lịch sử chat mặc định `size = 4` và `page` bắt đầu từ 0, trong khi các endpoint khác bắt đầu từ 1. | Dễ gây lỗi lệch trang. |
| B7 | `orderId` là chuỗi ở `EscrowHoldRequest` nhưng là số ở `EscrowHoldResponse`, trong khi `Order.id` trên `feature/order` là UUID. | Contract không nhất quán, và escrow hiện chưa thể gắn với order thật. |
| B8 | Các tài liệu thiết kế cho chat, offer, vòng đời order, payment/escrow và upload ảnh đều rỗng (0 byte) trên `main`. | Frontend không có contract viết sẵn cho các phần này. |
| B9 | Tên class `UserProfileReSponse` bị viết sai chính tả. | Chỉ là thẩm mỹ, nội bộ, không ảnh hưởng JSON. |
| B10 | Đăng ký đặt user ở `PENDING_VERIFICATION`, đăng nhập chỉ cho `ACTIVE`, và không có endpoint xác minh nào. Chỉ admin (qua `PUT /users/{id}/status`) hoặc tài khoản admin seed mới có `ACTIVE`. Team xác nhận ngày 2026-11-08. | User tự đăng ký xong không đăng nhập được. Frontend chưa thể làm luồng đăng ký rồi đăng nhập hoàn chỉnh. |
| B11 | `GET /api/v1/orders/{orderId}` không kiểm tra người gọi có phải chủ order. Mọi user đã đăng nhập đều xem được order của người khác (tên, số điện thoại, địa chỉ nhận hàng). `GET /checkout-groups/{id}` và `GET /payments/{id}` thì có kiểm tra. | Lộ dữ liệu cá nhân. Backend cần sửa trước khi phát hành. |
| B12 | Sau khi SePay báo thành công chỉ `Payment` đổi sang `SUCCESSFUL`. `PaymentCompletedEvent` không có listener, `CheckoutGroup.markPaid()` và `markExpired()` không được gọi, order vẫn `PENDING_PAYMENT`, và không có job hết hạn. | Team dự định gắn listener cho `PaymentCompletedEvent` (chưa có code, ghi nhận 2026-11-08). Cho đến khi đó được merge và ghi vào tài liệu này, Frontend chỉ tin được `paymentStatus` của payment, không tin `status` của order hay checkout group. |
| B13 | `GET /api/v1/orders/me` trả 404 (`ORDER_003`) khi người mua chưa có order. | Trạng thái rỗng bị trả như lỗi. Frontend phải xử lý riêng. |
| B14 | Checkout chưa giữ hay trừ tồn kho (`//todo`), chưa áp voucher, phí ship luôn 0. `ProductResponse` không có trường số lượng tồn nhưng checkout nhận `quantity` từ 1 đến 100 (team đã quyết định bỏ `quantity`, xem Q17). | Có thể bán trùng cùng một sản phẩm cho nhiều người. Cần cơ chế giữ hàng hoặc khoá sản phẩm khi đã có checkout. |

---

## 7. Câu hỏi nghiệp vụ và kỹ thuật còn mở

| ID | Câu hỏi | Chặn việc gì |
|---|---|---|
| Q1 | Frontend biết **permission code** của user bằng cách nào (và với staff thì biết thuộc gian hàng nào)? `GET /stores/my-store/staff/me` yêu cầu `STORE_STAFF_MANAGE`, mà một staff bình thường có thể không có quyền này. | Menu theo permission trong Store Dashboard. |
| Q2 | Frontend lưu access/refresh token ở đâu (bộ nhớ, `localStorage`, hay cookie httpOnly)? Backend trả cả hai trong JSON body, nên dùng cookie sẽ phải sửa Backend. | Doc 08 và lớp Axios. |
| Q3 | **Đã trả lời từ source** (xem 3.2): khi gặp `AUTH_007`, Backend thu hồi toàn bộ phiên của user. Cách UI thông báo cho user sẽ ghi ở doc 08. | Doc 08. |
| Q4 | Ai gọi escrow `hold` / `capture` / `release`: service order nội bộ hay Frontend? | Frontend có cần các endpoint này không. |
| Q5 | Ảnh product upload bằng cách nào (API chỉ nhận URL HTTPS)? Pre-signed URL? Dịch vụ bên thứ ba? | Trang tạo/sửa product. |
| Q6 | Chat có gắn với offer, order hay product không? Hiện conversation chỉ là `(participant1, participant2)`. | Điểm vào chat trên giao diện. |
| Q7 | `listingId` trong cart có đúng là `Product.id` không? | Liên kết cart với product. |
| Q8 | Thương lượng offer: số vòng, thời hạn, ai được counter? | Quy tắc giao diện offer. `REQUIRES_BUSINESS_CLARIFICATION`. |
| Q9 | Refund: ai được yêu cầu, có những trạng thái nào, có hoàn một phần không? Requirements nói Admin xử lý refund thủ công; hiện chưa có API. | Các trang refund. |
| Q10 | Quy trình dispute chưa được định nghĩa. | Các trang dispute giữ ở `PLANNED`. |
| Q11 | **Đã biết một phần** (mục 5.8): QR là URL ảnh do Backend trả, trạng thái lấy bằng polling `GET /payments/{id}`. Còn thiếu: thông tin chuyển khoản dạng chữ (số tài khoản, ngân hàng, nội dung), tần suất polling hợp lý, và cách xử lý chuyển khoản trễ hoặc sai số tiền. | Trang thanh toán. |
| Q12 | Branch nào là đích tích hợp, và khi nào `order`, `payment`, `notification`, `chat`, `cart`, `voucher`, `pricing`, `escrow-wallet` được merge vào `main`? Lưu ý `feature/order` đã gộp `main`, `chat`, `voucher`, `escrow-wallet` nhưng **không có** `cart`, `notification`, `pricing`. | Quyết định khi nào các dòng `IN_PROGRESS` được phép code. |
| Q13 | `isRead` trong JSON của notification được serialize thành `read` hay `isRead`? | Hiển thị danh sách notification. |
| Q14 | Luồng kích hoạt tài khoản sau khi đăng ký là gì (email xác minh, OTP, hay admin duyệt)? Xem B10. | Trang đăng ký, đăng nhập và thông báo sau đăng ký. |
| Q15 | **Team đang bàn lại (2026-11-08), chưa có quyết định.** Ai chuyển order qua `CONFIRMED`, `SHIPPING`, `DELIVERED`? Người bán xác nhận và giao hàng thế nào? Người mua xác nhận đã nhận hàng và huỷ order ở đâu? Hiện chỉ có admin `cod-collected`. | Trang quản lý order của Store Dashboard và timeline order của người mua. |
| Q16 | Cart dùng `listingId`, checkout dùng `productId` và nhận danh sách item riêng, không đọc từ cart. Sau checkout có xoá item khỏi cart không? Voucher áp dụng ở checkout thế nào (code hiện đang comment)? | Luồng cart sang checkout. |
| Q17 | **Đã quyết định (2026-11-08):** đồ cũ chỉ bán 1 món cho mỗi sản phẩm, bỏ `quantity`. Backend chưa sửa code (branch hiện vẫn nhận `quantity` 1 đến 100), nên contract checkout ở mục 5.7 sẽ đổi. Frontend không làm ô chọn số lượng. **Còn mở:** khi một sản phẩm đang nằm trong checkout chờ thanh toán của người này, người khác có đặt được không (giữ hàng trong 15 phút hay không)? | Form checkout, trang chi tiết sản phẩm. |
| Q18 | COD: ai thu tiền và khi nào (endpoint tên `cod-collected` nhưng chuyển `DELIVERED` sang `COMPLETED`)? Escrow có áp dụng cho order COD và order trả bằng chuyển khoản không, và nối với order bằng cách nào? | Trang order, trang escrow của admin. |

---

## 8. Bảo trì tài liệu

- Cập nhật tài liệu này **trong cùng Pull Request** mỗi khi một branch Backend được merge hoặc một Frontend service được thêm.
- Khi một dòng đổi trạng thái, ghi lại commit hoặc PR của Backend làm căn cứ.
- Giữ file này giống hệt nhau ở cả hai repo Frontend.
