# Moldy Market — Security Design
---

## 1. Authentication

### 1.1 Email + Password

- Password được hash bằng **BCrypt** trước khi lưu vào DB.
- Không bao giờ lưu hoặc log plain-text password.
- Khi đăng ký, tài khoản ở trạng thái `PENDING` cho đến khi xác minh OTP qua email.

### 1.2 Google OAuth 2.0

- Sử dụng **Authorization Code Flow**.
- Backend exchange `code` với Google để lấy profile — không bao giờ nhận `access_token` từ client.
- Nếu email đã tồn tại trong DB → link tài khoản. Nếu chưa → tạo mới với `password_hash = NULL`.

### 1.3 JWT

| Token | Thuật toán | TTL | Lưu ở đâu |
|---|---|---|---|
| Access Token | HS256 | 15 phút | Memory (không lưu localStorage) |
| Refresh Token | Random UUID | 7 ngày | HttpOnly Cookie |

**Payload của Access Token:**
```json
{
  "sub": "userId",
  "role": "USER",
  "isAppraiser": true,
  "isStoreOwner": false,
  "storeId": null,
  "workspaceType": "APPRAISER",
  "iat": 1726567890,
  "exp": 1726568790
}
```

**Refresh Token flow:**
```
Client gửi Refresh Token (qua HttpOnly Cookie)
    │
    ▼
Backend validate:
  - Token còn tồn tại trong DB?
  - Chưa bị revoke?
  - Chưa hết hạn?
    │
    ├── OK → Issue Access Token mới
    └── FAIL → Xóa cookie, yêu cầu đăng nhập lại
```

**Revoke Refresh Token:**
- Khi user đăng xuất → xóa refresh token trong DB.
- Khi admin khóa tài khoản → xóa tất cả refresh token của user đó.
- Khi phát hiện đăng nhập bất thường → xóa toàn bộ session.

### 1.4 OTP (One-Time Password)

| Trigger | Kênh gửi | TTL | Max sai |
|---|---|---|---|
| Đăng ký xác minh email | Email | 10 phút | 5 lần |
| Quên mật khẩu | Email | 10 phút | 3 lần |
| Rút tiền | Email hoặc SMS | 5 phút | 3 lần |
| Đổi số điện thoại | SMS (SĐT cũ) | 5 phút | 3 lần |
| Đổi tài khoản ngân hàng | Email + SMS | 5 phút | 3 lần |
| Admin đăng nhập | Email | 5 phút | 3 lần |

**Lockout policy:** Sau khi sai quá số lần cho phép:
- Hành động rút tiền: khóa 15 phút.
- Đăng nhập Admin: khóa 30 phút, ghi log.

---

## 2. Authorization

### 2.1 Role & Permission Model

Moldy Market không dùng nhiều role cứng nhắc. Thay vào đó dùng **flags + runtime check**:

| Field trên `users` | Ý nghĩa |
|---|---|
| `role = ADMIN` | Toàn quyền trên Admin Portal |
| `role = USER` | Người dùng thông thường |
| `is_appraiser = true` | Được phép nhận nhiệm vụ thẩm định |
| `is_store_owner = true` | Sở hữu gian hàng |
| `status = BLOCKED` | Mọi action đều bị từ chối |

Store Staff không có entry trong `users.role` — quyền của họ lưu trong bảng `store_staff.permissions`.

### 2.2 Permission Matrix

| Resource / Action | Guest | User | Store Staff | Store Owner | Appraiser | Admin |
|---|---|---|---|---|---|---|
| Xem listing | Được | Được | Được | Được | Được | Được |
| Tạo listing | Không | Được | Được (trong shop) | Được | Được | Được |
| Sửa/xóa listing | Không | Chỉ của mình | Chỉ trong shop | Chỉ trong shop | Chỉ của mình | Được |
| Gửi offer | Không | Được | Không | Không (phải switch) | Được | Không |
| Mua hàng | Không | Được | Không | Không (phải switch) | Được | Không |
| Rút tiền | Không | Được | Không | Được (shop wallet) | Được | Không |
| Nhận thẩm định | Không | Không | Không | Không | Được | Không |
| Quản lý staff | Không | Không | Không | Được | Không | Được |
| Khóa tài khoản | Không | Không | Không | Không | Không | Được |
| Phân xử tranh chấp | Không | Không | Không | Không | Không | Được |
| Duyệt Appraiser | Không | Không | Không | Không | Không | Được |

### 2.3 Ownership Check

Ngoài role, mọi action trên resource đều phải kiểm tra **ownership** tại service layer:

```
// Seller chỉ được sửa listing của mình
if (listing.sellerId != currentUserId) → throw ForbiddenException

// Staff chỉ được thao tác trong scope shop của mình
if (storeStaff.storeId != requestedStoreId) → throw ForbiddenException

// Buyer chỉ xem đơn hàng của mình
if (order.buyerId != currentUserId && order.sellerId != currentUserId) → throw ForbiddenException
```

### 2.4 Workspace Context

Khi user có nhiều vai trò (Store Owner + Appraiser), JWT chứa `workspaceType` để xác định context hiện tại:

- `workspaceType = NORMAL` → Hành động với tư cách cá nhân.
- `workspaceType = STORE` → Hành động trong scope gian hàng.
- `workspaceType = APPRAISER` → Hành động trong scope Appraiser.

Backend kiểm tra `workspaceType` để enforce đúng permission.

---

## 3. API Security

### 3.1 JWT Validation tại API Gateway

Spring Cloud Gateway validate JWT trên mọi request trước khi forward xuống backend:

```
Request đến Gateway
    │
    ├── Endpoint public (/api/listings GET, /api/search, ...)
    │       → Forward thẳng, không cần token
    │
    └── Endpoint protected (/api/orders, /api/wallet, ...)
            │── Extract JWT từ Authorization header
            │── Verify signature (HS256)
            │── Kiểm tra exp
            │── Forward kèm decoded claims
            └── Nếu invalid → 401 Unauthorized
```

### 3.2 Rate Limiting

Thực hiện tại API Gateway, lưu counter trong memory (đủ cho single instance):

| Endpoint | Giới hạn |
|---|---|
| `POST /auth/login` | 10 lần / phút / IP |
| `POST /auth/register` | 5 lần / phút / IP |
| `POST /auth/otp/send` | 3 lần / 5 phút / user |
| `POST /payments/webhook` | Không giới hạn (IP whitelist SePay) |
| `GET /listings/search` | 60 lần / phút / IP |
| Các API khác | 100 lần / phút / user |

### 3.3 Input Validation

- Tất cả input được validate bằng **Jakarta Bean Validation** (`@NotNull`, `@Size`, `@Pattern`,...) trước khi xử lý.
- Parameterized queries qua JPA/Hibernate — không bao giờ nối string SQL thủ công.
- Sanitize HTML content trong `title`, `description` của listing để chặn XSS.
- File upload: kiểm tra MIME type và file extension, giới hạn size (ảnh tối đa 10MB).

### 3.4 CORS Policy (chưa chốt domain)

```
Allowed Origins:
  - https://moldymarket.vn
  - https://admin.moldymarket.vn
  - http://localhost:3000 (chỉ development)

Allowed Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
Allowed Headers: Authorization, Content-Type
Allow Credentials: true (cho Refresh Token cookie)
```

### 3.5 HTTPS

- Tất cả traffic bắt buộc HTTPS.
- HTTP tự động redirect về HTTPS.
- Admin Portal: xem xét thêm IP whitelist ở tầng Gateway.

---

## 4. Payment Security

### 4.1 SePay Webhook Verification

Mọi request đến endpoint `/api/payments/webhook` đều phải được verify trước khi xử lý:

```
POST /api/payments/webhook
    │
    ├── Kiểm tra IP source có nằm trong whitelist SePay không?
    │       Không → 403 Forbidden
    │
    ├── Verify signature header (HMAC-SHA256 với secret key)
    │       Không khớp → 400 Bad Request
    │
    ├── Parse body: { transferCode, amount, bankTxnId, ... }
    │
    ├── Tìm payment theo transferCode
    │       Không tìm thấy → 404 (nhưng return 200 để SePay không retry)
    │
    ├── Kiểm tra bankTxnId đã xử lý chưa? (Idempotency)
    │       Đã có → return 200 ngay, không xử lý lại
    │
    ├── Kiểm tra amount khớp với payment.amount không?
    │       Không khớp → Ghi log cảnh báo, không cập nhật
    │
    └── Xử lý trong DB Transaction:
            UPDATE payment SET status=SUCCESS, bankTxnId=...
            UPDATE order SET status=PAID_AWAITING_PREPARATION
            → Commit
```

**Secret key** lưu trong environment variable, không hardcode trong code.

### 4.2 Escrow State Transitions

Chỉ các actor sau mới được trigger transition trên `escrow_status`:

| Transition | Trigger bởi | Điều kiện |
|---|---|---|
| `→ HELD` | SePay webhook | Payment SUCCESS |
| `HELD → RELEASED` | Scheduler (tự động) | Order COMPLETED + hết thời gian đổi trả |
| `HELD → REFUNDED` | System (khi hủy đơn) | Order CANCELLED |
| `HELD → FROZEN` | Admin | Dispute OPENED |
| `FROZEN → RELEASED` | Admin | Phân xử → nhả tiền cho seller |
| `FROZEN → REFUNDED` | Admin | Phân xử → hoàn tiền cho buyer |

Mọi transition được thực hiện trong **DB Transaction** để đảm bảo atomicity.

### 4.3 Withdrawal Security

```
User yêu cầu rút tiền
    │
    ├── Kiểm tra tài khoản ngân hàng đã liên kết?
    ├── Kiểm tra số dư khả dụng >= số tiền rút?
    ├── Gửi OTP về email/SĐT
    │
    │── User nhập OTP
    │       Sai → tăng fail_count
    │       Sai >= 3 lần → lock 15 phút
    │       Đúng → tiếp tục
    │
    └── Tạo wallet_transaction type=WITHDRAWAL
        → Trừ số dư
        → Gửi lệnh chuyển tiền (xử lý async)
```

---

## 5. Data Security

### 5.1 Password

- Hash bằng **BCrypt**, cost factor **12**.
- Không log, không trả về trong bất kỳ response nào.
- Reset password qua link gửi email (token UUID, TTL 30 phút, single-use).

### 5.2 Thông tin ngân hàng

- Lưu dạng JSON: `{ bankName, accountNumber, accountName }`.
- Mã hóa toàn bộ JSON bằng **AES-256-GCM** trước khi lưu vào cột `bank_account`.
- Encryption key lưu trong environment variable (không trong code, không trong DB).
- Khi cần hiển thị: chỉ trả về số tài khoản đã mask (VD: `****1234`).

### 5.3 S3 Access Control

| Path | Cơ chế | Ghi chú |
|---|---|---|
| `listings/**` | Public read | Qua CloudFront CDN |
| `users/*/avatar/**` | Public read | Qua CloudFront CDN |
| `disputes/**` | Private | Chỉ truy cập qua Presigned URL, TTL 1 giờ |
| `appraisers/**` | Private | Chỉ backend IAM role + Admin |
| `stores/*/logo/**` | Public read | Qua CloudFront CDN |

**Presigned URL** được sinh tại backend, không bao giờ expose AWS credentials ra client.

### 5.4 Sensitive Data trong Log

Không được log các thông tin sau:
- Password, OTP code
- JWT token
- Thông tin ngân hàng (kể cả đã mã hóa)
- SePay webhook secret
- AWS credentials

Log chỉ ghi `userId`, `action`, `timestamp`, `result` — không ghi payload nhạy cảm.

---

## 6. Sensitive Actions & 2FA

Những action sau yêu cầu xác thực OTP bổ sung ngoài JWT:

| Action | OTP gửi về | TTL | Lockout sau N sai |
|---|---|---|---|
| Rút tiền | Email + SMS | 5 phút | 3 lần → lock 15 phút |
| Đổi số điện thoại | SMS (SĐT cũ) | 5 phút | 3 lần |
| Đổi tài khoản ngân hàng | Email + SMS | 5 phút | 3 lần |
| Admin đăng nhập | Email | 5 phút | 3 lần → lock 30 phút |

**Flow chung:**

```
1. User thực hiện action nhạy cảm
2. Backend gửi OTP (lưu hashed OTP + expiry vào DB/memory)
3. User submit OTP
4. Backend verify:
     - OTP khớp hash?
     - Chưa hết TTL?
     - fail_count < max?
5. OK → thực hiện action, xóa OTP khỏi DB
   FAIL → tăng fail_count
          Nếu >= max → lock + thông báo user
```

---

## 7. Appraiser Conflict of Interest

Đây là rule nghiệp vụ quan trọng, được enforce tại **service layer** — không phụ thuộc vào frontend.

### Rule 1: Không thẩm định sản phẩm của chính mình

```java
// AppraisalService.java
if (appraisalRequest.getSellerId().equals(currentUserId)) {
    throw new ForbiddenException("Appraiser cannot appraise their own listing");
}
```

Kiểm tra trong `AppraisalService` khi Appraiser nhận nhiệm vụ.

### Rule 2: Không mua sản phẩm mình đã thẩm định

```java
// OrderService.java
boolean hasAppraised = appraisalRequestRepository
    .existsByListingIdAndAppraiserId(listingId, currentUserId);

if (hasAppraised) {
    throw new ForbiddenException("Appraiser cannot purchase a listing they have appraised");
}
```

Kiểm tra trong `OrderService` khi tạo order.

### Rule 3: Không nhận nhiệm vụ đã bị người khác nhận

Dùng **SELECT FOR UPDATE** (pessimistic lock) khi Appraiser nhận nhiệm vụ để tránh race condition:

```sql
SELECT * FROM appraisal_requests
WHERE id = ? AND status = 'WAITING'
FOR UPDATE;
-- Nếu status không còn là WAITING → rollback, thông báo "không còn khả dụng"
```

---

## 8. Audit & Logging

### 8.1 Những action cần ghi audit log

| Action | Thông tin ghi |
|---|---|
| Admin đăng nhập | `adminId`, `ip`, `timestamp`, `success/fail` |
| Admin khóa/mở tài khoản | `adminId`, `targetUserId`, `action`, `reason`, `timestamp` |
| Admin phân xử tranh chấp | `adminId`, `disputeId`, `decision`, `note`, `timestamp` |
| Admin thay đổi Legit Points | `adminId`, `targetUserId`, `delta`, `reason`, `timestamp` |
| Admin duyệt/từ chối Appraiser | `adminId`, `appraiserId`, `decision`, `reason`, `timestamp` |
| Rút tiền | `userId`, `amount`, `bankAccount` (masked), `timestamp`, `status` |
| Thay đổi thông tin ngân hàng | `userId`, `timestamp`, `oldMasked`, `newMasked` |
| Store Owner thêm/xóa Staff | `storeOwnerId`, `staffUserId`, `action`, `permissions`, `timestamp` |
| Escrow state transition | `orderId`, `fromStatus`, `toStatus`, `triggeredBy`, `timestamp` |

### 8.2 Log Format

Sử dụng structured logging (JSON) để dễ query:

```json
{
  "timestamp": "2026-09-17T10:30:00Z",
  "level": "AUDIT",
  "action": "ADMIN_LOCK_ACCOUNT",
  "actorId": "admin-uuid",
  "targetId": "user-uuid",
  "metadata": {
    "reason": "Multiple fraud complaints",
    "ip": "192.168.1.1"
  },
  "result": "SUCCESS"
}
```

### 8.3 Những thứ KHÔNG được log

- Plain-text password
- OTP code
- JWT token (kể cả partial)
- Số tài khoản ngân hàng đầy đủ
- AWS credentials, API keys
- SePay webhook secret

### 8.4 Log Retention

| Loại log | Thời gian giữ |
|---|---|
| Audit log (Admin action) | 2 năm |
| Payment log | 5 năm (yêu cầu pháp lý) |
| Application error log | 90 ngày |
| Access log | 30 ngày |
