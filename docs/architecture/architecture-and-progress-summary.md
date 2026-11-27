# MoldyMarket — Báo cáo Tổng kết Tiến độ & Quy chuẩn Thiết kế Hệ thống

> **Tài liệu tham khảo đồ án / Kiến trúc kỹ thuật backend**  
> **Dự án**: MoldyMarket Backend (Spring Boot 4.1 / Spring Security 7.x / Java 23 / PostgreSQL / DynamoDB)\
> **Cập nhật đến**: 2026-11-27 (`main` `f3ca06a` — 29 domain packages, bao gồm Offer Negotiation, Offer-Chat STOMP bridge, File Storage, Reviews, Wishlist, Disputes, Analytics)

---

## PHẦN 1. TỔNG HỢP NHỮNG GÌ ĐÃ HOÀN THÀNH

Từ đầu dự án đến 2026-11-22, hệ thống đã hợp nhất toàn bộ các module lõi vào `main` (PR #6 `integration/merge-all` và các bản vá sau merge) cùng đợt tích hợp chọn lọc checkout/payment/order/chat trên nhánh local `codex/selective-checkout-integration`:

### 1.1. Security Audit, Authentication, Email OTP & Rate Limiting
- **SEC-001 → SEC-007**: JWT secret 512-bit trong `.env`, loại bỏ log raw token, tối ưu `JwtAuthenticationFilter` (đọc `Authorization: Bearer <token>`, kiểm tra `isTokenValid`, `isEnabled`, `isAccountNonLocked`), xử lý chuẩn 401/403.
- **HttpOnly Refresh Cookie & CSRF Header**: Refresh token được chuyển từ JSON body sang HttpOnly cookie `moldy_refresh` (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`). `/login`, `/refresh`, `/logout` bắt buộc header `X-Moldy-Auth: 1` và trả `Cache-Control: no-store`. `/login` và `/refresh` trả `AccessTokenResponse` (không chứa `refreshToken` trong JSON).
- **Email OTP Verification**:
  - `POST /api/v1/auth/register`: Tạo tài khoản `PENDING_VERIFICATION`, gán role `USER`, tạo `Wallet` mặc định và gửi mã OTP 6 chữ số (hết hạn 5 phút, tối đa 5 lần sai). Đăng ký lại email chưa xác minh trả `403 AUTH_008` (`EMAIL_NOT_VERIFIED`).
  - `POST /api/v1/auth/verify-email`: Xác minh `{ email, code, password }` và kích hoạt tài khoản `ACTIVE`.
  - `POST /api/v1/auth/resend-verification`: Gửi lại OTP (giãn cách tối thiểu 60 giây).
- **Rate Limiting (`common/ratelimit`)**: Áp dụng Bucket4j trên `register`, `login` (per-IP & per-account), `resend/verify-email`, `products/search`, `products/suggest`.
- **CORS (`SecurityConfig`)**: Đã cấu hình CORS cho `/api/**` theo `${app.cors.allowed-origins}`. *(Lưu ý mở: chưa bật `setAllowCredentials(true)` và chưa thêm `X-Moldy-Auth` vào `allowedHeaders`; chạy qua Vite proxy same-origin bình thường nhưng cross-origin cookie cần bổ sung).*

### 1.2. Role System & Hệ thống khởi tạo tự động (Seeders)
- Định nghĩa 5 Role chuẩn: `USER`, `APPRAISER`, `STAFF`, `ADMIN`, `OWNER`.
- `RoleInitializer`: Chạy `@Order(1)` lúc khởi động app, tự động seed 5 roles vào bảng `roles` và khởi tạo tài khoản **System Admin** mặc định từ biến môi trường (`.env`).
- Luồng đăng ký: Tự động gán role `USER` cho tài khoản mới trong cùng transaction.

### 1.3. User Profile & Quản trị User (User Module)
- `GET /api/v1/users/me`: Xem hồ sơ cá nhân.
- `PUT /api/v1/users/me`: Cập nhật họ tên, số điện thoại (kiểm tra trùng SĐT).
- `PUT /api/v1/users/me/password`: Đổi mật khẩu (xác thực mật khẩu cũ, cấm trùng mật khẩu mới).
- `GET /api/v1/users`: Quản trị viên xem danh sách người dùng với phân trang `Slice` (không dùng Count query, tối ưu performance).
- `GET /api/v1/users/{id}`: Xem chi tiết thông tin tài khoản theo ID.
- `PUT /api/v1/users/{id}/status`: Admin khóa / mở khóa tài khoản (`ACTIVE`, `LOCKED`, `SUSPENDED`).

### 1.4. Phân quyền chi tiết RBAC (Role-Based Access Control)
- **Mô hình 3 loại quyền (GrantType)**: `FULL` (mặc định theo role), `CONDITIONAL` (kèm điều kiện nghiệp vụ), `DELEGATED` (ủy quyền động).
- `PermissionConstants`: Hằng số String chuẩn hóa hơn 30 mã quyền từ bảng thiết kế `MoldyMarket_Permission_Table.md`.
- `PermissionInitializer`: Chạy `@Order(2)` tự động seed toàn bộ quyền và ánh xạ Role ↔ Permission vào bảng `role_permissions`.
- `CustomUserDetailsService`: Tự động nạp đồng thời cả Role (`ROLE_...`) lẫn Permission vào danh sách `GrantedAuthority`.
- `GlobalExceptionHandler`: Bắt `AccessDeniedException` trả về đúng mã **HTTP 403 FORBIDDEN** (thay vì 500), token sai trả về **HTTP 401 UNAUTHORIZED**.

### 1.5. Store Module (Quản lý Gian hàng — Phase 1)
- `POST /api/v1/stores`: Đăng ký mở gian hàng (mỗi user tối đa 1 shop, tên shop độc nhất).
  - **Tự động cấp role `OWNER` kèm `storeId`** vào bảng `user_roles`.
- `GET /api/v1/stores/my-store`: Chủ shop xem thông tin gian hàng của mình.
- `PUT /api/v1/stores/my-store`: Chủ shop cập nhật tên, mô tả, địa chỉ lấy hàng (chặn khi shop bị khóa).
- `GET /api/v1/stores/{id}`: Public API xem thông tin gian hàng (cho người mua, không cần token).
- `GET /api/v1/stores`: Admin xem danh sách tất cả gian hàng (phân trang Slice).
- `PUT /api/v1/stores/{id}/status`: Admin khóa / mở khóa gian hàng (`ACTIVE`, `BLOCKED`).

### 1.6. Store Staff Module (Quản lý Nhân viên Gian hàng — Phase 2)
- Bảng `store_staff`: Lưu quan hệ Store ↔ User nhân viên kèm danh sách quyền được ủy quyền (`permissions` dạng `Set<String>`).
- `POST /api/v1/stores/my-store/staff`: Owner thêm nhân viên bằng Email + cấp các nhóm quyền ban đầu:
  - Chặn không cho Owner tự thêm chính mình.
  - Chặn thêm trùng nhân viên vào cùng 1 shop.
  - Whitelist quyền hợp lệ cho Staff (`PRODUCT_CREATE`, `APPRAISAL_REQUEST_CREATE`, `ORDER_PROCESS`, `ORDER_CANCEL`, `OFFER_HANDLE`, `CHAT_SEND`).
  - Luôn tự động kèm quyền `STORE_WORKSPACE_ACCESS`.
  - Đồng bộ cấp role `STAFF` kèm `storeId` trong `user_roles`.
- `GET /api/v1/stores/my-store/staff`: Owner xem danh sách nhân viên của shop mình.
- `PUT /api/v1/stores/my-store/staff/{staffId}/permissions`: Owner cập nhật / thay đổi quyền nhân viên.
- `DELETE /api/v1/stores/my-store/staff/{staffId}`: Owner sa thải nhân viên (xóa khỏi `store_staff` và xóa role `STAFF` trong `user_roles`).
- `GET /api/v1/stores/my-store/staff/me`: Nhân viên tự xem hồ sơ và các quyền được cấp của mình (yêu cầu `STORE_WORKSPACE_ACCESS`).
- **Nạp quyền ủy quyền động**: `CustomUserDetailsService` tự động đọc bảng `store_staff` để nạp các quyền ủy quyền vào Security Context mỗi khi nhân viên gửi request. `StoreAccessGuard` kiểm tra membership và quyền ủy quyền đúng gian hàng (tách biệt `PRODUCT_MANAGE_OWN` khỏi `PRODUCT_CREATE`).

### 1.7. Category & Product Module
- **Category (`CategoryController`)**: Public `GET /api/v1/categories` (tree) & `GET /{id}`; Admin (`CATEGORY_MANAGE`) `POST`, `PUT /{id}`, `DELETE /{id}` (`204 Void`).
- **Product (`ProductController`)**:
  - Public: `GET /api/v1/products` (`ProductFilterRequest` + `Pageable`), `GET /{id}`, `GET /search` (`ProductSearchResponse` có facets, rate-limited), `GET /suggest` (rate-limited).
  - Seller/Store: `POST /api/v1/products` (`PRODUCT_CREATE`, yêu cầu `storeId`), `PUT /{id}` (yêu cầu `version` cho optimistic lock, `409 COMMON_010`), `PATCH /{id}/status`, `DELETE /{id}` (`204 Void`), `GET /api/v1/products/me`, `GET /api/v1/stores/my-store/products`, `GET /api/v1/stores/{storeId}/products`.
  - Checkout reservation: `Product.reservedCheckoutId` và `Product.reservationExpiresAt` chặn sửa/ẩn/xóa sản phẩm đang bị giữ chỗ (`409 PROD_012`).
  - Admin (`PRODUCT_MODERATE`): `PUT /{id}/remove` (kèm `reason`), `PUT /{id}/restore`.

### 1.8. Cart, Checkout, Order, Store Orders & Payment (SePay + COD)
- **Cart (`CartController`)**: Toàn bộ endpoint `/api/v1/carts/**` (single-unit `quantity = 1` cho đồ cũ). Chặn chủ shop/nhân viên thêm sản phẩm của shop mình (`403 CART_001`).
- **Checkout & Order (`CheckoutGroupController`, `OrderController`, `StoreOrderController`)**:
  - `POST /api/v1/checkout-groups` & `GET /api/v1/checkout-groups/{id}`: Khóa sản phẩm theo thứ tự UUID, giữ chỗ 15 phút cho thanh toán online (không deadline tự động cho COD), chặn tự mua (`403 ORDER_012`).
  - `GET /api/v1/orders/me` *(vẫn trả `404 ORDER_003` khi rỗng — B13)*, `GET /api/v1/orders/{orderId}` (đã enforce `requireBuyer`), `POST /api/v1/orders/buyer/{orderId}/cancel`.
  - `GET /api/v1/stores/{storeId}/orders` (phân trang `PagedResponse`, owner hoặc staff có `ORDER_PROCESS`), `POST /api/v1/orders/seller/{orderId}/confirm` (chỉ owner cho COD `PENDING_CONFIRMED`).
  - Admin (`@IsAdmin`): `PATCH /api/v1/orders/admin/{orderId}/start-shipping`, `PATCH .../mark-delivered`, `POST .../cod-collected`.
  - `CheckoutExpirationScheduler`: Quét mỗi 60 giây để hết hạn checkout online chưa thanh toán và giải phóng reservation.
- **Payment & SePay (`PaymentController`, `PaymentTransactionController`)**:
  - `GET /api/v1/payments/my-payments`, `GET /api/v1/payments/{id}` (kèm `method`, `paymentStatus`, `qrBankUrl`).
  - `POST /api/v1/payment_transactions/webhook/{provider}` (HMAC verified, raw JSON response). `PaymentCompletedEventListener` cập nhật đồng bộ `CheckoutGroup`, `Order`, `Product` (`SOLD`) và dọn item đã mua khỏi giỏ hàng trong cùng transaction.

### 1.9. Chat Realtime (REST + STOMP/SockJS) & Notification (DynamoDB)
- **Chat (`ConversationController`, `ChatController`)**:
  - REST: `POST/GET /api/v1/conversations` (kèm tên participant, `canSend`, `sendBlockedReason`, ID cursors, `unreadCount`), `GET /{id}/messages` (`page` 0-based, mặc định `size=4`), `PATCH /{id}/read` (yêu cầu `{ messageId }`).
  - WebSocket STOMP tại `/chat` (SockJS): Client gửi `/app/conversations/{conversationId}/send`, nhận trên `/user/queue/messages`, `/user/queue/errors`, `/user/queue/chat-reads`. `StompAuthInterceptor` kiểm tra JWT trên `CONNECT`, `SEND` và `SUBSCRIBE`. `ChatAccessPolicy` chặn chat nội bộ staff–owner cùng shop (`403 CHAT_005`) và participant không hợp lệ (`403 CHAT_006`).
- **Notification (`NotificationController`)**: Lưu trên DynamoDB, phân trang cursor (`limit` 1..50, `token`), hỗ trợ `GET`, `GET /{id}`, `PATCH /{id}/read`, `DELETE /{id}`, `PATCH /{id}/restore`.

### 1.10. Wallet, Escrow, Withdrawal, Voucher & Pricing
- **Wallet / Escrow / Withdrawal (`WalletController`, `EscrowController`, `WithdrawalController`)**: Đã merge lên `main`, nhưng **chưa có `@PreAuthorize` cho các hành động admin/escrow (B2 vẫn mở)**.
- **Voucher (`VoucherController`, prefix `/api/...`)**: `GET /api/vouchers/{code}` (hiện yêu cầu đăng nhập do chưa nằm trong `permitAll`), Admin (`@IsAdmin`) `POST/GET /api/admin/vouchers`, `GET /api/admin/vouchers/{code}`.
- **Pricing (`AutoPricingController`, `ReferencePriceController`, prefix `/api/...`)**: `POST /api/pricing/auto` (`@IsUser`), CRUD `/api/admin/reference-prices` (`@IsAdmin`).

### 1.11. Appraiser, Appraisal Lifecycle, Commission & File Storage
- **Appraiser (`AppraiserController`, `AdminAppraiserController`)**: `/api/v1/appraisers/**` (đăng ký, hồ sơ, active list, deactivate/reactivate) và `/api/v1/admin/appraisers/**` (`hasRole('ADMIN')`: list, pending, approve, reject, suspend, resume).
- **Appraisal Lifecycle (`appraisal/*`)**: Đầy đủ 6 controller: `AppraisalRequestController`, `AppraiserApplicationController`, `AppraisalInvitationController`, `AppraisalAssignmentController`, `AppraisalReportController`, `AppraisalReviewController`. *(Lưu ý: `AppraisalConversationService` vẫn đang trả `UUID.randomUUID()` placeholder; rule chặn appraiser tự thẩm định sản phẩm của shop mình sở hữu/làm staff mới được duyệt nghiệp vụ, chưa enforce đầy đủ trên toàn bộ luồng).*
- **Commission (`AppraiserCommissionController`)**: `/api/v1/appraiser/commissions/**` (trả raw JSON, không bọc `ApiResponse`).

### 1.12. Offer / Thương lượng giá & Cầu nối Chat thời gian thực (2026-11-27)
- **Offer (`OfferController`, `OfferService`, `OfferExpirationScheduler`)**: Đầy đủ 10 endpoint dưới `/api/v1/offers/**` (tạo đề nghị, counter, accept, reject, cancel, `/sent`, `/received`, `/product/{productId}/active`, `/{id}`, `/{id}/history`) với `@PreAuthorize` (`OFFER_CREATE` / `OFFER_HANDLE`), phân quyền ủy quyền `StoreAccessGuard`, tối thiểu 75% giá gốc, tối đa 3 lượt người mua, hạn phản hồi 24h và hạn đặt hàng sau khi chốt 24h.
- **Product `allowOffer`**: Cột `products.allow_offer` (`boolean not null default true`) cấu hình bật/tắt thương lượng khi đăng/sửa sản phẩm.
- **Checkout & Voucher Lock**: `CheckoutGroupService` tự áp giá `ACCEPTED` offer (liên kết `checkoutGroupId`) và chặn cộng dồn mã giảm giá (`400 OFF_010` `OFFER_VOUCHER_NOT_ALLOWED`).
- **Chat Offer Card Mirroring (`OfferChatEventListener`)**: Cột `message.offer_id` + `attached_product_id` (lộ ra trong `MessageResponse`). Mỗi sự kiện Offer tự ghi/cập nhật thẻ Offer trong cuộc hội thoại Buyer ↔ Chủ gian hàng và đẩy thời gian thực qua STOMP `/user/queue/messages`. Chi tiết tại `docs/detailed-design/offer-negotiation.md` và `docs/detailed-design/chat.md`.
- **File Storage (`common/storage/controller/FileController`)**: `/api/v1/files/presigned-url`, `/{fileId}/confirm`, `/{fileId}/download-url`, `DELETE /{fileId}` (trả raw JSON, không bọc `ApiResponse`).

---

## PHẦN 2. QUY CHUẨN THIẾT KẾ THƯ MỤC & PHÂN TẦNG (ARCHITECTURE)

Dự án áp dụng mô hình **Package by Feature (Modular Monolith)** với **24 packages cấp 1** kết hợp phân tầng trách nhiệm rõ ràng (Layered Architecture):

```
com.moldy.moldymarket/
├── common/                     # Hạ tầng dùng chung (exception, response, ratelimit, storage)
├── config/                     # SecurityConfig (JWT + CORS), PaginationConfig, Seeders
├── security/                   # jwt, refreshToken, user, annotation (@IsAdmin, @IsUser, @IsAppraiser...)
├── user/ | role/ | userrole/ | permission/   # Định danh, OTP, RBAC
├── store/                      # Gian hàng, StoreStaff, StoreAccessGuard
├── category/ | product/        # Danh mục, Sản phẩm, Search/Suggest, Reservation
├── cart/ | order/ | payment/   # Giỏ hàng, CheckoutGroup, Order, StoreOrder, SePay webhook
├── chat/ | notification/       # STOMP WebSocket chat + Read cursors, DynamoDB Notification
├── wallet/ | voucher/ | pricing/ # Ví/Escrow/Rút tiền, Voucher, AutoPricing & ReferencePrice
├── appraiser/ | appraisal/ | commission/ # Hồ sơ thẩm định viên, 6 bước thẩm định, Hoa hồng
├── dispute/ | refund/          # Entity/placeholder cho giai đoạn sau (chưa mở API)
└── controller/                 # TestController (/api/v1/test/protected)
```

### Nguyên tắc phân tầng:
1. **Controller**: Chỉ tiếp nhận HTTP request, parse tham số, validate bằng `@Valid`, phân quyền bằng `@PreAuthorize`, gọi Service và trả về `ResponseEntity<ApiResponse<T>>`. Tuyệt đối không viết logic nghiệp vụ hay query database ở Controller.
2. **Service**: Chứa 100% logic nghiệp vụ. Validate business rules, ném `AppException(ErrorCode.XYZ)`, gọi Repository, chuyển đổi sang DTO qua Mapper. Luôn có `@Transactional(readOnly = true)` trên class và `@Transactional` trên phương thức ghi/sửa/xóa.
3. **Repository**: Kế thừa `JpaRepository`, chỉ đảm nhiệm việc đọc/ghi DB. Sử dụng JPQL tối ưu khi cần join nhiều bảng hoặc fetch dữ liệu cụ thể để tránh lỗi N+1.
4. **Mapper**: Dùng MapStruct interface để sinh code ánh xạ compile-time, không dùng reflection (như ModelMapper) để đảm bảo tốc độ cao nhất.

---

## PHẦN 3. QUY TẮC THIẾT KẾ CÁC LỚP (DESIGN CONVENTIONS)

### 3.1. Request DTO — Luôn dùng Java `record`
Request DTO chỉ là túi dữ liệu trung chuyển, bất biến (immutable) và cần ngắn gọn:
```java
public record AddStaffRequest(
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be valid")
    String email,

    Set<String> permissions
) {}
```
- **Lý do**: Không cần boilerplate code (`getter`, `setter`, `equals`, `hashCode`, `toString`).
- Spring MVC tự động deserialize JSON vào record. Các annotation Bean Validation (`@NotBlank`, `@Size`, `@NotNull`) hoạt động hoàn hảo.
- Trong Service truy cập dữ liệu trực tiếp qua cú pháp `request.email()` (không có tiền tố `get`).

### 3.2. Response DTO & MapStruct
- Response DTO có thể là `record` hoặc `class`:
  - Nếu là `class`: Cần có `@Getter` và `@Setter` để MapStruct gán giá trị qua setter.
  - Nếu là `record`: MapStruct 1.6+ tự động map vào canonical constructor.
- Tách biệt Mapper thành Interface có `@Mapper(componentModel = "spring")`:
```java
@Mapper(componentModel = "spring")
public interface StoreStaffMapper {
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "storeId", source = "store.id")
    @Mapping(target = "email", source = "user.email")
    StaffResponse toResponse(StoreStaff staff);
}
```
- Khi thêm/sửa field trong DTO, nếu MapStruct sinh code cũ dẫn đến response bị `null`, giải pháp là **Rebuild Project** / `mvn clean compile` để MapStruct sinh lại file `*Impl.java`.

### 3.3. JPA Entity
- Trường ID dùng `UUID` với chiến lược sinh tự động:
  ```java
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;
  ```
- Dùng `@FieldDefaults(level = AccessLevel.PRIVATE)` của Lombok để ngầm định mọi field là private, tránh gõ thừa `private`.
- Không lạm dụng `@Setter` bừa bãi trên các trường nhạy cảm như `id`, `ownerId`, `createdAt`.
- Quan hệ giữa các module độc lập: Ưu tiên lưu `UUID` (như `Store.ownerId`, `UserRole.storeId`) thay vì quan hệ `@ManyToOne` cứng nhắc nếu hai module không cần phụ thuộc chặt chẽ về vòng đời dữ liệu.

---

## PHẦN 4. CƠ CHẾ PHÂN QUYỀN RBAC (ROLE VS PERMISSION)

Đây là điểm sáng kiến trúc của hệ thống:

```
[User] ──(gán)──> [Role] ──(quy định)──> [Permission] ──(bảo vệ)──> [Endpoint]
  │                                           ▲
  └──(ủy quyền trực tiếp qua StoreStaff)───────┘
```

### 4.1. Sự khác biệt giữa Role và Permission:
- **Role (Vai trò)**: Đại diện cho "Người dùng là ai trong tổ chức" (`USER`, `APPRAISER`, `OWNER`, `STAFF`, `ADMIN`). Quản trị viên quản lý theo Role để không phải đi tick hàng chục quyền lẻ cho từng tài khoản.
- **Permission (Quyền hạn)**: Đại diện cho "Hành động cụ thể nào được phép thực thi" (`PRODUCT_CREATE`, `USER_STORE_MANAGE`, `ORDER_PAY`...). Endpoint gắn ổ khóa bằng Permission.

### 4.2. Cơ chế nạp quyền kết hợp (Hybrid Authorities Loading):
Khi một request gửi token lên server, `CustomUserDetailsService.buildUserDetails()` thực hiện 3 bước:
1. **Chuyển các Role thành Authority**: Tiền tố `ROLE_` (ví dụ `ROLE_USER`, `ROLE_OWNER`, `ROLE_STAFF`).
2. **Nạp các quyền cố định (FULL GrantType)**: Truy vấn 1 câu JPQL từ bảng `role_permissions` lấy toàn bộ mã quyền tương ứng với các role mà user nắm giữ.
3. **Nạp các quyền ủy quyền (DELEGATED)**: Nếu user là Store Staff, đọc thêm danh sách quyền riêng lẻ trong bảng `store_staff` nạp tiếp vào danh sách.

### 4.3. Cách bảo vệ Endpoint:
- Dùng `@PreAuthorize` kết hợp hằng số từ `PermissionConstants`:
```java
// Chỉ ai có quyền STORE_STAFF_MANAGE (mặc định là OWNER) mới được vào
@PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
@PostMapping("/my-store/staff")
public ResponseEntity<?> addStaff(...) { ... }

// Cả OWNER (có sẵn trong role) và STAFF (được ủy quyền) đều dùng chung ổ khóa này:
@PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_CREATE + "')")
@PostMapping("/products")
public ResponseEntity<?> createProduct(...) { ... }
```

---

## PHẦN 5. QUY CHUẨN XỬ LÝ LỖI & PHẢN HỒI (EXCEPTION HANDLING)

### 5.1. Định dạng Response chuẩn: `ApiResponse<T>` (và các ngoại lệ hiện có)
Phần lớn REST API thành công hay thất bại đều trả về cấu trúc thống nhất:
```json
{
    "success": true,
    "code": "SUCCESS",
    "message": "Request completed successfully",
    "data": { ... },
    "timestamp": "2026-10-12T09:00:00Z"
}
```
**Các ngoại lệ không bọc `ApiResponse<T>`** (Frontend cần lưu ý):
- `FileController` (`/api/v1/files/**`): trả DTO trực tiếp hoặc `204 Void`.
- `AppraiserCommissionController` (`/api/v1/appraiser/commissions/**`): trả raw `List` / DTO trực tiếp.
- `PaymentTransactionController` (`/api/v1/payment_transactions/webhook/{provider}`): trả raw provider body (ví dụ `{"success": true}`).
- `TestController` (`/api/v1/test/protected`): trả raw `Map<String, Object>`.
- Các endpoint `204 No Content` không có body (`DELETE` category/product/file, `PATCH/DELETE` notification, `PATCH` chat read, `DELETE` reference price).

**Phân trang:** Hệ thống hiện có 4 kiểu: `PagedResponse` (1-based `page`), `ProductSearchResponse` (1-based `page` + `facets`), Spring `Page` thô (lịch sử chat nhận `page` 0-based thủ công, còn appraisal/admin reference-prices/appraisers dùng `PaginationConfig` 1-based request nhưng Spring `Page.number` trả 0-based), và cursor `NotificationPageResponse` (`limit` + `token`).

---

## PHẦN 6. CÁC ĐIỂM CÒN MỞ / CẦN LƯU Ý (2026-11-22)

1. **Thiếu `@PreAuthorize` (B2):** `WalletController`, `EscrowController`, và `WithdrawalController` (gồm cả `/approve`, `/complete`, `/reject`, `/fail`) mới chỉ yêu cầu `authenticated()`, chưa khóa quyền admin/ownership ở controller.
2. **CORS cross-origin cookie:** `SecurityConfig` đã bật CORS trên `/api/**`, nhưng chưa có `setAllowCredentials(true)` và chưa thêm `X-Moldy-Auth` vào `allowedHeaders`.
3. **`GET /api/v1/orders/me` (B13):** Vẫn trả `404 ORDER_003` khi danh sách đơn rỗng (Frontend đang xử lý fallback về `[]`).
4. **Appraisal:** `AppraisalConversationService` đang trả `UUID.randomUUID()` placeholder (chưa nối chat thật); chính sách chặn appraiser thẩm định sản phẩm của chính mình / shop sở hữu / shop làm staff đã được duyệt nghiệp vụ nhưng chưa enforce đầy đủ trên toàn bộ luồng.
5. **`permitAll` còn thiếu so với mô tả cũ:** `GET /api/vouchers/{code}`, `GET /api/v1/appraisers/active`, `GET /api/v1/appraisers/{appraiserId}` hiện yêu cầu đăng nhập (không nằm trong `SecurityConfig.permitAll()`).

### 5.2. Quản lý mã lỗi tập trung: `ErrorCode`
Mọi lỗi nghiệp vụ đều được khai báo trong enum `ErrorCode` với 3 thông số: mã lỗi (`code`), thông điệp mặc định (`message`), và mã trạng thái HTTP (`httpStatus`):
```java
STORE_NOT_FOUND("STORE_001", "Store not found", HttpStatus.NOT_FOUND),
STORE_ALREADY_EXISTS("STORE_002", "You already have a store", HttpStatus.CONFLICT),
STAFF_NOT_FOUND("STORE_005", "Staff not found in this store", HttpStatus.NOT_FOUND),
```

### 5.3. Bắt lỗi tự động tại `GlobalExceptionHandler`:
- Bắt `AppException`: Rút `errorCode`, đóng gói vào `ApiResponse.failure()` và trả về đúng HTTP Status tương ứng.
- Bắt `MethodArgumentNotValidException`: Trích xuất danh sách lỗi validation theo từng field (`validationErrors`).
- Bắt `AccessDeniedException` (Spring Security): Trả về HTTP 403 `FORBIDDEN`.
- Bắt lỗi token (`CustomAuthenticationEntryPoint`): Trả về HTTP 401 `UNAUTHENTICATED`.
- Bắt lỗi không lường trước (`Exception.class`): Trả về HTTP 500 `INTERNAL_SERVER_ERROR` và ghi log ERROR.
