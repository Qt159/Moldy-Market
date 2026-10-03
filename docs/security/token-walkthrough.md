# MoldyMarket — Authentication System Documentation

> **Dành cho**: Toàn bộ team developer  
> **Cập nhật lần cuối**: 2026-09-23  
> **Stack**: Spring Boot 4.1 · Spring Security · JJWT 0.12.6 · PostgreSQL

---

## 1. Kiến trúc Token

```
┌─────────────┐       JWT (stateless)        ┌──────────────┐
│   Client    │ ─── Authorization: Bearer ──► │  API Server  │
│             │                               │              │
│  lưu 2 thứ:│ ◄── accessToken + refresh ─── │  DB: chỉ lưu │
│  · accessT  │        Token (raw)            │  tokenHash   │
│  · refreshT │                               │  (SHA-256)   │
└─────────────┘                               └──────────────┘
```

| Token | Dạng | TTL | Lưu ở đâu | Mục đích |
|-------|------|-----|-----------|----------|
| **Access Token** | JWT (HS256) | 15 phút | Client only | Xác thực mỗi request |
| **Refresh Token** | Opaque random (86 ký tự Base64) | 30 ngày | Client (raw) + DB (SHA-256 hash) | Lấy access token mới |

> **Quan trọng**: DB **không bao giờ** lưu raw refresh token — chỉ lưu SHA-256 hash. Raw token chỉ tồn tại trong response về client và trong memory lúc xử lý.

---

## 2. Các Flow chính

### 2.1 Register Flow

```
POST /api/v1/auth/register
Body: { email, password, fullName, phone? }

Client ──► AuthController.register()
            ├─ normalize email (trim + toLowerCase)
            ├─ check email duplicate → 409 nếu trùng
            ├─ BCrypt encode password
            ├─ save User (status = PENDING_VERIFICATION)
            ├─ lookup role "USER" từ DB
            ├─ save UserRole(user, USER, storeId=null)
            └─ return RegisterResponse (không có token)

Response 201:
{
  "userId": "...",
  "email": "...",
  "fullName": "...",
  "status": "PENDING_VERIFICATION"
}
```

> ⚠️ User mới ở trạng thái `PENDING_VERIFICATION` — **chưa login được** cho đến khi được activate (email verification chưa implement).

---

### 2.2 Login Flow

```
POST /api/v1/auth/login
Body: { email, password }

Client ──► AuthController.login()
            ├─ authenticationManager.authenticate(email, password)
            │   ├─ loadUserByUsername(email) → DB query (user + roles)
            │   ├─ BCrypt.matches(password, hash)
            │   ├─ isEnabled() → false nếu PENDING → DisabledException
            │   └─ isAccountNonLocked() → false nếu LOCKED → LockedException
            │
            ├─ [nếu thất bại]
            │   ├─ DisabledException  → 403 ACCOUNT_DISABLED
            │   ├─ LockedException   → 403 ACCOUNT_LOCKED
            │   └─ AuthenticationException → 401 INVALID_CREDENTIALS
            │
            ├─ [nếu thành công]
            │   ├─ lấy User từ Authentication.getPrincipal() (không query DB lần 2)
            │   ├─ generateAccessToken(user) → JWT HS256, TTL 15 phút
            │   └─ issueRefreshToken(user) → random 64 bytes, hash SHA-256, save DB
            │
            └─ return AuthResponse

Response 200:
{
  "userId": "...",
  "email": "...",
  "fullName": "...",
  "accessToken": "eyJ...",
  "refreshToken": "abc123...",
  "tokenType": "Bearer"
}
```

---

### 2.3 Access Token Validation Flow (mỗi request)

```
Request với header: Authorization: Bearer <accessToken>

JwtAuthenticationFilter (chạy trước mọi request)
  ├─ không có header → bỏ qua, tiếp tục filter chain
  │
  └─ có Bearer token:
      ├─ extractUserId(token) → parse JWT, verify HMAC signature + expiration
      │   └─ fail → log warn, clear SecurityContext, tiếp tục (sẽ bị 401 sau)
      │
      ├─ loadUserById(userId) → DB query (user + roles)
      ├─ isTokenValid(token) → kiểm tra claim "tokenType" == "ACCESS"
      ├─ userDetails.isEnabled() → status == ACTIVE
      ├─ userDetails.isAccountNonLocked() → status != LOCKED
      │
      └─ [nếu tất cả pass]
          └─ set UsernamePasswordAuthenticationToken vào SecurityContext
```

**Trade-off**: Mỗi authenticated request đều query DB để load user + roles. Đây là **cố ý** để:
- Roles luôn fresh (không cache stale role trong JWT)
- User bị disable/lock có hiệu lực trong vòng tối đa 15 phút (TTL access token)

---

### 2.4 Refresh Token Flow

```
POST /api/v1/auth/refresh
Body: { refreshToken: "abc123..." }

Client ──► AuthController.refresh()
            ├─ hash(rawToken) → SHA-256
            ├─ findByTokenHash(hash) → DB lookup
            │   └─ không tìm thấy → 401 REFRESH_TOKEN_INVALID
            │
            ├─ isRevoked() == true?
            │   └─ YES → revokeAllActiveByUserId() (revoke TẤT CẢ session)
            │            → 401 REFRESH_TOKEN_REUSED  ← phát hiện token bị đánh cắp
            │
            ├─ isExpired() == true?
            │   └─ 401 REFRESH_TOKEN_EXPIRED
            │
            ├─ [hợp lệ] rotation:
            │   ├─ generate newRawToken (64 bytes SecureRandom)
            │   ├─ newHash = SHA-256(newRawToken)
            │   ├─ existing.revoke(newHash)    ← đánh dấu token cũ là đã dùng
            │   ├─ save existing (revoked=true, replacedByTokenHash=newHash)
            │   └─ save new RefreshToken(user, newHash, now+30d)
            │
            ├─ generateAccessToken(user) → JWT mới
            └─ return AuthResponse (token mới)
```

> 🔐 **Reuse Detection**: Nếu hacker lấy được refresh token cũ (đã rotate) và dùng lại → hệ thống phát hiện, **revoke toàn bộ session** của user đó, buộc đăng nhập lại trên tất cả thiết bị.

---

### 2.5 Logout Flow

```
POST /api/v1/auth/logout
Body: { refreshToken: "abc123..." }

Client ──► AuthController.logout()
            ├─ hash(rawToken) → SHA-256
            ├─ findByTokenHash(hash) → nếu tìm thấy:
            │   └─ token.revoke(null) + save
            └─ return 200 (idempotent — không cần token tồn tại)
```

> **Lưu ý**: Logout chỉ revoke **1 session** (refresh token hiện tại). Access token vẫn valid đến khi hết hạn (tối đa 15 phút). Để logout tất cả thiết bị, cần gọi endpoint riêng (chưa implement).

---

## 3. Role System

### Các Role hiện có

| Role | Mô tả (dự kiến) |
|------|----------------|
| `USER` | Người dùng thông thường — gán tự động khi register |
| `APPRAISER` | Thẩm định viên |
| `STAFF` | Nhân viên cửa hàng |
| `ADMIN` | Quản trị viên hệ thống |
| `OWNER` | Chủ cửa hàng |

### Cách roles được load

- Roles **không** được nhúng vào JWT — JWT chỉ chứa `userId` và `tokenType`
- Mỗi request → filter load roles từ DB qua `CustomUserDetailsService.loadUserById()`
- Authorities format: `ROLE_USER`, `ROLE_ADMIN`, ... (Spring Security convention)
- `UserRole` có thêm `storeId` — phục vụ phân quyền theo store (chưa dùng)

### Trạng thái UserStatus

```
PENDING_VERIFICATION  →  Mới đăng ký, chưa verify email
       ↓ (activate)
    ACTIVE            →  Có thể login bình thường
       ↓ (lock)
    LOCKED            →  Bị khóa, không thể login
```

---

## 4. Hướng dẫn sử dụng cho Team

### 4.1 Gọi API — Thứ tự chuẩn

```bash
# 1. Đăng ký
POST /api/v1/auth/register
{ "email": "...", "password": "...", "fullName": "..." }

# 2. Login (user phải ở trạng thái ACTIVE)
POST /api/v1/auth/login
{ "email": "...", "password": "..." }
# → nhận được accessToken + refreshToken

# 3. Gọi API có bảo vệ
GET /api/v1/...
Authorization: Bearer <accessToken>

# 4. Khi accessToken hết hạn (15 phút) → refresh
POST /api/v1/auth/refresh
{ "refreshToken": "..." }
# → nhận accessToken mới + refreshToken mới (token cũ bị invalidate)

# 5. Logout
POST /api/v1/auth/logout
{ "refreshToken": "..." }
```

---

### 4.2 Cách thêm endpoint mới có phân quyền

Khi thêm endpoint cần role check, dùng `@PreAuthorize` trên Controller:

```java
// Chỉ ADMIN mới được vào
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/users")
public ResponseEntity<?> getAllUsers() { ... }

// Nhiều role được phép
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@GetMapping("/store/orders")
public ResponseEntity<?> getOrders() { ... }

// Lấy thông tin user đang đăng nhập
@GetMapping("/me")
public ResponseEntity<?> getMe(Authentication authentication) {
    CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
    UUID userId = userDetails.getUserId();
    // ...
}
```

> Để `@PreAuthorize` hoạt động, cần thêm `@EnableMethodSecurity` vào `SecurityConfig`.

---

### 4.3 Error Codes — Auth

| Code | HTTP | Ý nghĩa |
|------|------|---------|
| `INVALID_CREDENTIALS` | 401 | Sai email/password |
| `ACCOUNT_DISABLED` | 403 | Account chưa được verify hoặc disabled |
| `ACCOUNT_LOCKED` | 403 | Account bị khóa |
| `REFRESH_TOKEN_INVALID` | 401 | Refresh token không tồn tại trong DB |
| `REFRESH_TOKEN_EXPIRED` | 401 | Refresh token đã hết 30 ngày |
| `REFRESH_TOKEN_REUSED` | 401 | Dùng lại token đã rotate — toàn bộ session bị revoke |
| `EMAIL_ALREADY_EXISTS` | 409 | Email đã đăng ký |
| `ROLE_NOT_FOUND` | 500 | DB thiếu data roles (roles chưa được seed) |
| `UNAUTHENTICATED` | 401 | Request không có hoặc có JWT không hợp lệ |
| `ACCESS_DENIED` | 403 | Không đủ quyền truy cập endpoint |

---

### 4.4 Cấu hình Token TTL

Trong file `.env`:

```env
JWT_SECRET=<64+ ký tự random>
```

Trong `application-{profile}.yaml`:

```yaml
security:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiration: 900000      # 15 phút (milliseconds)
    refresh-token-expiration: 2592000000 # 30 ngày (milliseconds)
```

---

### 4.5 Database Schema — Auth tables

```sql
-- Bảng refresh token (lưu hash, không lưu raw token)
refresh_tokens
  id                     UUID PK
  user_id                UUID FK → users(id)
  token_hash             VARCHAR(128) UNIQUE   -- SHA-256 hex của raw token
  expires_at             TIMESTAMP
  revoked                BOOLEAN DEFAULT false
  replaced_by_token_hash VARCHAR(128)          -- chain để forensics
  created_at             TIMESTAMP

-- Bảng roles
roles
  id    UUID PK
  name  VARCHAR(50) UNIQUE  -- USER | APPRAISER | STAFF | ADMIN | OWNER

-- Bảng gắn user ↔ role
user_roles
  id        UUID PK
  user_id   UUID FK → users(id)
  role_id   UUID FK → roles(id)
  store_id  UUID nullable    -- phân quyền theo store (dùng sau)
```
