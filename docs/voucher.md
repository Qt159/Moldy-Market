# Voucher System

## Overview
Admin tạo mã giảm giá, khách hàng kiểm tra tính hợp lệ và sử dụng khi thanh toán.
---

## API Endpoints

### 1. Check Voucher (Public - No Auth)
```http
GET /api/vouchers/{code}
```
**Response**: `{ code, discountType, discountValue, expiredAt, valid }`

**Examples**:
```bash
# Valid voucher
GET /api/vouchers/SALE20
→ 200 OK
{
  "data": {
    "code": "SALE20",
    "discountType": "PERCENT",
    "discountValue": 20,
    "expiredAt": "2026-12-31T23:59:59Z",
    "valid": true
  }
}

# Expired voucher (not an error, just invalid)
GET /api/vouchers/EXPIRED
→ 200 OK
{ ..., "valid": false }

# Not found
GET /api/vouchers/NOTEXIST
→ 404 Not Found
{ "code": "VOUCHER_001", "message": "Voucher not found" }
```

---

### 2. Create Voucher (Admin Only)
```http
POST /api/admin/vouchers
Authorization: Bearer {ADMIN_TOKEN}
Content-Type: application/json
```

**Request**:
```json
{
  "code": "SALE20",
  "discountType": "PERCENT",
  "discountValue": 20,
  "remainingQuantity": 1000,
  "expiredAt": "2026-12-31T23:59:59Z"
}
```

**Response**: `201 Created`
```json
{
  "message": "Voucher created successfully",
  "data": {
    "code": "SALE20",
    "discountType": "PERCENT",
    "discountValue": 20,
    "remainingQuantity": 1000,
    "expiredAt": "2026-12-31T23:59:59Z"
  }
}
```

**Errors**:
- `409 Conflict` — Duplicate code
- `400 Bad Request` — Invalid input (discountValue ≤ 0, quantity ≤ 0, etc.)

---

### 3. Get All Vouchers (Admin Only)
```http
GET /api/admin/vouchers
Authorization: Bearer {ADMIN_TOKEN}
```

**Response**: `200 OK`
```json
{
  "data": [
    { "code": "SALE20", "discountType": "PERCENT", "discountValue": 20, "remainingQuantity": 1000, "expiredAt": "..." },
    { "code": "FIXED50K", "discountType": "FIXED", "discountValue": 50000, "remainingQuantity": 200, "expiredAt": "..." }
  ]
}
```

---

### 4. Get Voucher by Code (Admin Only)
```http
GET /api/admin/vouchers/{code}
Authorization: Bearer {ADMIN_TOKEN}
```

**Response**: `200 OK` with full voucher details (including remainingQuantity)

---

## Data Model

### Voucher Fields
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | Auto-generated PK |
| code | String | Unique, uppercase, max 50 chars |
| discountType | PERCENT / FIXED | Type of discount |
| discountValue | BigDecimal | 20 for 20% or 50000 for 50K VND |
| remainingQuantity | Integer | Stock available |
| version | Integer | Optimistic locking (concurrency) |
| expiredAt | Instant | UTC timestamp, must be future |
| createdAt | Instant | Auto-set, immutable |
| updatedAt | Instant | Auto-updated |

### DiscountType Enum
- `PERCENT` — Percentage discount (0-100%)
- `FIXED` — Fixed amount in VND

---

## Validation Rules

| Field | Rule | Error |
|-------|------|-------|
| code | @NotBlank, unique, ≤50 chars | COMMON_001 (400) |
| discountType | @NotNull | COMMON_001 (400) |
| discountValue | @NotNull, > 0 | COMMON_001 (400) |
| remainingQuantity | @Positive (> 0) | COMMON_001 (400) |
| expiredAt | @NotNull | COMMON_001 (400) |

**Code Normalization**: Automatically trimmed + uppercase

---

## Error Codes

| Code | HTTP | Scenario |
|------|------|----------|
| VOUCHER_001 | 404 | Voucher not found |
| VOUCHER_002 | 409 | Duplicate code |
| VOUCHER_003 | 422 | Expired (for service layer) |
| VOUCHER_004 | 422 | Out of stock (for service layer) |
| COMMON_001 | 400 | Validation failed |
| COMMON_003 | 401 | Missing authentication |
| COMMON_004 | 403 | Forbidden (non-admin) |

---

## Key Behaviors

### 1. Code Normalization
Input: `"  SALE20  "`
Stored: `"SALE20"` (trimmed + uppercase)

Benefit: Case-insensitive, prevents duplicate with different cases

### 2. Public Check Endpoint
- Shows: `code, discountType, discountValue, expiredAt, valid`
- Hides: `remainingQuantity` (prevents stock enumeration attacks)
- Returns: `valid: true/false` (not error)

### 3. Admin View
- Shows: All fields including `remainingQuantity`
- Requires: `@IsAdmin` role

### 4. Expiry Check
A voucher is **valid** if:
- Not expired (`expiredAt >= now`)
- Has stock (`remainingQuantity > 0`)
- Exists

### 5. Optimistic Locking
`version` field prevents race conditions when multiple orders use same voucher simultaneously.

---

## Test Cases

### Happy Path
1. **Create voucher** → 201 Created 
2. **Check valid voucher** → 200 OK, valid=true 
3. **Get all vouchers** → 200 OK, list all 
4. **Get voucher by code** → 200 OK, full details 

### Error Cases
5. **Duplicate code** → 409 Conflict 
6. **Invalid discount** (≤0) → 400 Bad Request 
7. **Negative quantity** → 400 Bad Request 
8. **Not found** → 404 Not Found 

### Edge Cases
9. **Code normalization** → Stored as uppercase 
10. **Expired voucher** → valid=false (not error) 
11. **Out of stock** → valid=false (not error) 
12. **Fixed discount** → Amount in VND 
13. **Percent discount** → Decimal supported (15.5%) 

### Security
14. **Missing token** → 401 Unauthorized 
15. **Invalid token** → 401 Unauthorized 
16. **Non-admin access** → 403 Forbidden 

---

## For Testing in Postman

### Setup Variables
```
BASE_URL = http://localhost:8080
ADMIN_TOKEN = {your JWT from login}
```

### Sample Requests

**Create**:
```bash
POST {{BASE_URL}}/api/admin/vouchers
Authorization: Bearer {{ADMIN_TOKEN}}
Content-Type: application/json

{
  "code": "SALE20",
  "discountType": "PERCENT",
  "discountValue": 20,
  "remainingQuantity": 1000,
  "expiredAt": "2026-12-31T23:59:59Z"
}
```

**Check (Public)**:
```bash
GET {{BASE_URL}}/api/vouchers/SALE20
```

**List (Admin)**:
```bash
GET {{BASE_URL}}/api/admin/vouchers
Authorization: Bearer {{ADMIN_TOKEN}}
```

**Get by Code (Admin)**:
```bash
GET {{BASE_URL}}/api/admin/vouchers/SALE20
Authorization: Bearer {{ADMIN_TOKEN}}
```

---

## Database Schema

```sql
CREATE TABLE vouchers (
    id              UUID PRIMARY KEY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    discount_type   VARCHAR(10) NOT NULL,
    discount_value  DECIMAL(19,2) NOT NULL,
    remaining_quantity INTEGER NOT NULL,
    version         INTEGER NOT NULL,
    expired_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- Indexes
CREATE INDEX idx_voucher_code ON vouchers(code);
CREATE INDEX idx_voucher_expired_at ON vouchers(expired_at);

-- Constraints
ALTER TABLE vouchers ADD CONSTRAINT check_discount_value CHECK (discount_value > 0);
ALTER TABLE vouchers ADD CONSTRAINT check_quantity CHECK (remaining_quantity >= 0);
```

---

## Integration Example (Order Module)

```java
// Customer checks voucher
VoucherCheckResponse check = voucherService.checkVoucher(code);
if (!check.isValid()) {
    // Reject order
    return error("Voucher is not valid");
}

// Order creation - validate and get
Voucher voucher = voucherService.validateAndGet(code);

// Calculate discount
BigDecimal discount = calculateDiscount(orderTotal, voucher);

// After payment confirmed - decrease stock
voucherService.decreaseQuantity(voucher);
```

---

## Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| 401 Unauthorized | Set ADMIN_TOKEN variable in Postman |
| 403 Forbidden | Verify token has ADMIN role |
| 409 Duplicate | Use unique code or clear database |
| Timestamp error | Use ISO 8601 format with Z: `2026-12-31T23:59:59Z` |
| Code not normalized | Check service layer normalizes before save |

---