# Pricing Engine - Requirement Analysis

**Date Created:** 2026-10-09
**Last Updated:** 2026-10-09
**Status:** Initial Analysis

---

## 1. Overview

Pricing Engine support two pricing modes:
1. **Auto Pricing**: Automatic price calculation for popular electronics (phones, laptops) using reference price and condition factors
2. **Appraiser Pricing**: Manual pricing by qualified appraisers for any product category

This analysis categorizes all requirements from `docs/features/pricing-engine.md` into implementation status levels.

---

## 2. Requirement Classification Glossary

| Status | Meaning |
|---|---|
| **IMPLEMENTED** | Feature has been verified as fully implemented in current codebase |
| **TO_IMPLEMENT** | Clear requirement, within current scope, can be implemented now |
| **TODO** | Requirement exists but depends on missing modules/clarification, should be completed in next phase |
| **BLOCKED** | Cannot be implemented without external dependency or major architectural change |
| **AMBIGUOUS** | Documentation unclear or conflicting, needs clarification before implementation |

---

## 3. Core Business Logic Requirements

### REQ-001: Auto Pricing Eligibility Conditions
**Source:** Section 3.1
**Type:** Business Rule
**Description:** Auto Pricing applies only when ALL conditions are met:
- Category is sub-category of "Đồ điện tử" (Electronics)
- Supported categories: Điện thoại (Phone), Laptop
- Model exists in reference_prices table
- Origin = "OFFICIAL_VN"

**Dependencies:**
- Category entity with `is_electronic` flag
- Reference prices table with model data
- Category filtering in service

**Status:** `TO_IMPLEMENT`
**Test Cases:** REQ-001-TC01, REQ-001-TC02, REQ-001-TC03

---

### REQ-002: Auto Pricing Inputs
**Source:** Section 3.2, Table "Input"
**Type:** API Contract
**Description:** Auto Pricing endpoint accepts:
- `categoryId` (UUID, required)
- `modelId` (UUID, required)
- `origin` (Enum, required) = "OFFICIAL_VN"
- `warrantyStatus` (Enum) = UNDER_WARRANTY | EXPIRED_UNDER_1Y | EXPIRED_OVER_1Y
- `conditionLevel` (Enum) = NEW | SCRATCHED | PARTIAL_DAMAGE | HEAVILY_DAMAGED
- `usageMonths` (Integer ≥ 0, required)

**Status:** `TO_IMPLEMENT`
**Test Cases:** REQ-002-TC01 through REQ-002-TC06

---

### REQ-003: Auto Pricing Calculation Formula
**Source:** Section 3.3
**Type:** Business Logic
**Formula:** `suggestedPrice = referencePrice × deprecationFactor × conditionFactor × warrantyFactor`

**Deprecation Factors (by usageMonths):**
| Range | Factor |
|---|---|
| 0–3 | 0.90 |
| 4–6 | 0.82 |
| 7–12 | 0.72 |
| 13–18 | 0.63 |
| 19–24 | 0.55 |
| 25–36 | 0.45 |
| > 36 | 0.35 |

**Condition Factors:**
| Status | Factor |
|---|---|
| NEW | 1.00 |
| SCRATCHED | 0.90 |
| PARTIAL_DAMAGE | 0.70 |
| HEAVILY_DAMAGED | 0.45 |

**Warranty Factors:**
| Status | Factor |
|---|---|
| UNDER_WARRANTY | 1.05 |
| EXPIRED_UNDER_1Y | 0.97 |
| EXPIRED_OVER_1Y | 0.90 |

**Rounding:** Result rounded to nearest 10,000 VND (for Vietnamese market)

**Dependencies:**
- BigDecimal for precise calculations
- Rounding implementation

**Status:** `TO_IMPLEMENT`
**Test Cases:** REQ-003-TC01 through REQ-003-TC25 (boundary tests for all factor combinations)

---

### REQ-004: Auto Pricing Output
**Source:** Section 3.5
**Type:** API Contract
**Description:** Success response (status = SUGGESTED):
```json
{
  "pricingMode": "AUTO",
  "status": "SUGGESTED",
  "suggestedPrice": 17600000,
  "referencePrice": 28000000,
  "deprecationFactor": 0.72,
  "conditionFactor": 0.90,
  "warrantyFactor": 0.97,
  "referenceModelName": "iPhone 15 Pro Max",
  "referencePriceUpdatedAt": "2026-09-01T00:00:00Z",
  "disclaimer": "Giá tham khảo — không phải giá thẩm định chính thức"
}
```

Failure response (status = MANUAL_REQUIRED):
```json
{
  "pricingMode": "AUTO",
  "status": "MANUAL_REQUIRED",
  "reason": "MODEL_NOT_FOUND" | "CATEGORY_NOT_SUPPORTED",
  "suggestedPrice": null
}
```

**Status:** `TO_IMPLEMENT`
**Test Cases:** REQ-004-TC01, REQ-004-TC02

---

### REQ-005: Reference Price Management
**Source:** Section 3.3, 3.5, Database Design
**Type:** Admin Feature
**Description:**
- Reference price is current market price of model, updated by Admin every 1-3 months
- Database table: `reference_prices` with fields:
  - `id` (UUID, PK)
  - `category_id` (FK to categories)
  - `brand` (VARCHAR)
  - `model_name` (VARCHAR)
  - `reference_price` (BIGINT, in VND)
  - `currency` (DEFAULT 'VND')
  - `updated_at` (TIMESTAMPTZ)
  - `updated_by` (FK to users)

**Dependencies:**
- Reference prices entity
- Admin permissions
- PostgreSQL setup

**Status:** `TO_IMPLEMENT`
**Test Cases:** REQ-005-TC01 through REQ-005-TC04

---

### REQ-006: Appraiser Eligibility
**Source:** Section 4.2
**Type:** Business Rule
**Description:**
- User applies to become appraiser for specific categories
- Admin must approve before appraiser can work
- Appraiser can only accept requests in approved categories
- Appraiser cannot self-approve own request

**Dependencies:**
- AppraiserProfile entity
- AppraiserCategories mapping
- Admin approval workflow

**Status:** `BLOCKED` - requires full User role/permission system implementation

---

### REQ-007: Appraiser Pricing Flow
**Source:** Section 4.3
**Type:** Business Process
**Description:** Full flow from request creation to acceptance/rejection
- Seller creates listing
- Seller requests appraisal → system suggests appraisers OR seller chooses manually
- Appraiser receives notification
- Appraiser views images/description, can request additional details via chat
- Appraiser submits: suggested price + note
- Seller accepts → listing shows appraisal badge + commission recorded
- Seller rejects → can choose different appraiser (max 3 rejections per listing)

**Dependencies:**
- Listing entity
- Order/payment for commission settlement
- Notification system (SQS/SNS)
- Chat system
- Commission calculation

**Status:** `BLOCKED` - Listing, Order, Notification, Chat modules not yet implemented

---

### REQ-008: Appraiser Rating System
**Source:** Section 4.5
**Type:** Business Logic
**Description:**
- Reputation score based on accuracy of price predictions
- Only counted for completed transactions within ±20% of final sale price
- Formula: `accuracy% = (accurate_predictions / total_completed_transactions) * 100`
- Display rules:
  - ≥10 completed transactions: show percentage
  - <10 transactions: show "Người định giá mới — chưa đủ dữ liệu đánh giá"
- Score updated only after transaction completes

**Dependencies:**
- Order completion and confirmation
- Payment settlement module
- Appraiser profile with accuracy metrics

**Status:** `TODO` - depends on Order and Payment modules

---

### REQ-009: Commission Settlement
**Source:** Section 4.6
**Type:** Business Logic
**Description:**
- Commission rate agreed between seller and appraiser at request time
- Commission only charged on successful transaction completion
- Deducted from escrow during payout to seller
- Formula: `seller_receives = payment_amount - commission`, `appraiser_receives = commission`

**Dependencies:**
- Order/Payment module (escrow)
- Commission tracking in pricing request
- Payment settlement workflow

**Status:** `BLOCKED` - requires Order and Escrow modules

---

### REQ-010: Appraiser Output Response
**Source:** Section 4.4
**Type:** API Contract
**Description:** When seller accepts appraisal:
```
Đã tham khảo giá bởi [Appraiser Name] - [Reputation %]
```

Note: If <10 transactions:
```
Đã tham khảo giá bởi [Appraiser Name]
[Appraiser mới — chưa đủ dữ liệu đánh giá]
```

Plus disclaimer:
```
Đây là ý kiến về mức giá, không phải xác nhận về tính chính hãng hoặc chất lượng của sản phẩm.
```

**Status:** `TODO` - depends on reputation system

---

## 4. Database Schema Requirements

### REQ-DB-001: reference_prices Table
**Source:** Database Design, Section 5
**Status:** `TO_IMPLEMENT`
**Details:** See REQ-005

---

### REQ-DB-002: pricing_requests Table
**Source:** Database Design, Section 5
**Status:** `TO_IMPLEMENT`
**Schema:**
```sql
CREATE TABLE pricing_requests (
    id              UUID PRIMARY KEY,
    listing_id      UUID NOT NULL FK,
    pricing_mode    pricing_mode (AUTO|APPRAISER),
    status          pricing_status (SUGGESTED|MANUAL_REQUIRED|PENDING|SELLER_REJECTED|EXPIRED),
    
    -- Auto pricing input/output
    model_id            UUID FK,
    origin              VARCHAR(20),
    warranty_status     VARCHAR(30),
    condition_level     VARCHAR(20),
    usage_months        INTEGER,
    suggested_price     BIGINT,
    reference_price     BIGINT,
    deprecation_factor  NUMERIC(4,2),
    condition_factor    NUMERIC(4,2),
    warranty_factor     NUMERIC(4,2),
    
    -- Appraiser pricing
    appraiser_id        UUID FK,
    appraiser_note      TEXT,
    commission_rate     NUMERIC(5,2),
    reject_count        SMALLINT DEFAULT 0,
    
    disclaimer          TEXT,
    created_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ
);
```

---

### REQ-DB-003: appraiser_profiles Table
**Source:** Database Design, Section 5
**Status:** `TO_IMPLEMENT`
**Schema:**
```sql
CREATE TABLE appraiser_profiles (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL UNIQUE FK,
    status          appraiser_status (PENDING|ACTIVE|SUSPENDED),
    total_completed INTEGER DEFAULT 0,
    accurate_count  INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ
);
```

---

### REQ-DB-004: appraiser_categories Table
**Source:** Database Design, Section 5
**Status:** `TO_IMPLEMENT`
**Schema:**
```sql
CREATE TABLE appraiser_categories (
    appraiser_id UUID FK PRIMARY KEY PART 1,
    category_id  UUID FK PRIMARY KEY PART 2
);
```

---

## 5. API Endpoints

### REQ-API-001: POST /api/pricing/auto
**Source:** Section 6
**Status:** `TO_IMPLEMENT`
**Auth:** USER
**Request Body:** REQ-002
**Response:** REQ-004
**Validation:** REQ-001 (eligibility), REQ-002 (input), REQ-003 (calculation)

---

### REQ-API-002: POST /api/pricing/appraiser/request
**Source:** Section 6
**Status:** `BLOCKED` - Listing entity missing
**Auth:** USER (seller)
**Request Body:**
```json
{
  "listingId": "uuid",
  "appraiserId": "uuid",
  "commissionRate": 3.5
}
```
**Response:** `201` with pricing_request created, status = PENDING

---

### REQ-API-003: POST /api/pricing/appraiser/{requestId}/submit
**Source:** Section 6
**Status:** `BLOCKED` - Listing entity missing
**Auth:** APPRAISER
**Request Body:**
```json
{
  "suggestedPrice": 15000000,
  "note": "..."
}
```
**Response:** `200` with pricing_request updated, status = SUGGESTED

---

### REQ-API-004: POST /api/pricing/appraiser/{requestId}/accept
**Source:** Section 6
**Status:** `BLOCKED` - Listing entity missing
**Auth:** USER (seller)
**Behavior:** Mark pricing_request as ACCEPTED, display on listing

---

### REQ-API-005: POST /api/pricing/appraiser/{requestId}/reject
**Source:** Section 6
**Status:** `BLOCKED` - Listing entity missing
**Auth:** USER (seller)
**Behavior:** Mark as SELLER_REJECTED, increment reject_count, allow new request if count < 3

---

### REQ-API-006: GET /api/pricing/{listingId}
**Source:** Section 6
**Status:** `BLOCKED` - Listing entity missing
**Auth:** USER
**Response:** All pricing results (AUTO and APPRAISER) for listing

---

### REQ-API-007: GET /api/admin/reference-prices
**Source:** Section 6
**Status:** `TO_IMPLEMENT`
**Auth:** ADMIN
**Response:** List of all reference prices with pagination

---

### REQ-API-008: POST /api/admin/reference-prices
**Source:** Section 6
**Status:** `TO_IMPLEMENT`
**Auth:** ADMIN
**Request Body:**
```json
{
  "categoryId": "uuid",
  "brand": "Apple",
  "modelName": "iPhone 15 Pro Max",
  "referencePrice": 28000000
}
```
**Response:** `201` with created reference price

---

### REQ-API-009: PUT /api/admin/reference-prices/{id}
**Source:** Section 6
**Status:** `TO_IMPLEMENT`
**Auth:** ADMIN
**Request Body:**
```json
{
  "referencePrice": 27000000
}
```
**Response:** `200` with updated reference price

---

## 6. Validation & Error Handling

### REQ-VAL-001: Input Validation
**Source:** Section 7, 8
**Rules:**
- `usageMonths` ≥ 0
- `categoryId` and `modelId` must exist
- `origin` must be OFFICIAL_VN for Auto Pricing
- Commission rate must be positive and reasonable (0.1 - 50%)

**Error Codes:**
| Code | HTTP | Scenario |
|---|---|---|
| PRICING_001 | 404 | modelId not found |
| PRICING_002 | 404 | listingId not found |
| PRICING_003 | 404 | appraiserId not found |
| PRICING_004 | 422 | appraiserId not approved for category |
| PRICING_005 | 422 | 3 rejections already, cannot add new request |
| PRICING_006 | 422 | usageMonths < 0 |
| PRICING_007 | 409 | Pricing request already PENDING |
| PRICING_008 | 403 | Unauthorized (not appraiser assigned) |

**Status:** `TO_IMPLEMENT`

---

### REQ-VAL-002: Business Rule Validation
**Source:** Section 7
**Rules:**
- Auto Pricing: all eligibility conditions must be met (REQ-001)
- Appraiser: cannot exceed 3 rejections per listing
- Reference price must be > 0
- Deprecation/condition/warranty factors applied independently

**Status:** `TO_IMPLEMENT`

---

## 7. Documentation Conflicts & Ambiguities

### CONFLICT-001: Rounding Discrepancy
**Source:** Section 3.4, Example Calculation
**Issue:**
- Formula result: 28,000,000 × 0.72 × 0.90 × 0.97 = 17,599,680 VND
- Documentation claims ≈ 17,600,000 VND (rounded to 10,000)
- But API example shows `"suggestedPrice": 17590000` (not 17,600,000)

**Resolution Required:**
- Clarify if rounding is to nearest 10,000 or different logic
- Expected: 17,599,680 → 17,600,000 (round nearest 10k)
- But example shows 17,590,000 which suggests rounding DOWN or different calculation

**Decision for Implementation:**
Using "round to nearest 10,000" as stated in Section 3.4. API example may be illustrative only.

---

### CONFLICT-002: MANUAL_REQUIRED vs PRICING_001 Error Code
**Source:** Section 3.1, 3.5, Section 8
**Issue:**
- Section 3.1: "hệ thống không được thực hiện Auto Pricing và trả về MANUAL_REQUIRED"
- Section 8 lists error code PRICING_001 for "modelId không tồn tại"
- When modelId doesn't exist: should response be MANUAL_REQUIRED or PRICING_001 error?

**Resolution:**
- MANUAL_REQUIRED is a valid business state (not an error) - return HTTP 200 with status field
- PRICING_001 is error code - return HTTP 404 if modelId lookup fails
- Distinction: validation error (400/422) vs not found (404) vs business rule (200 with reason)

---

### CONFLICT-003: Reference Price Model Mapping
**Source:** Section 3.2, 3.3
**Issue:**
- Docs say `modelId` is UUID referencing reference_prices
- But reference_prices has (brand, model_name) unique, not a direct ID
- How is modelId matched to reference_prices? Is it reference_prices.id?

**Resolution:**
- modelId = reference_prices.id (UUID primary key)
- When creating/searching models, match by brand + model_name, return the ID

---

### CONFLICT-004: Appraiser Status Transitions
**Source:** Section 4.2, Section 4.3
**Issue:**
- No explicit state diagram for appraiser profile states (PENDING, ACTIVE, SUSPENDED)
- How does PENDING → ACTIVE transition? Manual admin approval only?
- What triggers SUSPENDED? Number of complaints?

**Resolution:**
- PENDING: Initial state after user applies
- ACTIVE: After admin approves
- SUSPENDED: Admin manual action (for policy violations)
- This is simplified; full behavior in TODO

---

### CONFLICT-005: PARTIAL_DAMAGE Semantics
**Source:** Section 3.2
**Issue:**
- Docs say PARTIAL_DAMAGE is "catch-all for any partial damage"
- Description encourages seller to add details in listing description
- But factor is fixed at 0.70 regardless of damage type
- This doesn't match real behavior (e.g., "pin worn out" vs "cracked screen" have different impact)

**Resolution:**
- Accepted limitation per Section 9 "Giới hạn đã biết"
- PARTIAL_DAMAGE is single category with fixed factor 0.70
- Detailed damage description is for buyer information, not pricing calculation

---

### AMBIGUITY-001: Commission Rate Precision
**Source:** Section 4.6
**Issue:**
- Docs show `commission_rate = 3.5` in example
- Should this be stored as percentage (3.5 = 3.5%) or decimal (3.5 = 350%)?
- Precision: 2 decimals enough or need more?

**Resolution:**
- Store as percentage: 3.5 = 3.5% (typical e-commerce commission)
- Type: NUMERIC(5,2) in PostgreSQL
- Calculation: `appraiser_receives = payment_amount * (commission_rate / 100)`

---

### AMBIGUITY-002: Reference Price Currency
**Source:** Section 3.3, Database Design
**Issue:**
- Docs assume VND (Vietnamese market)
- But schema has `currency` field defaulting to 'VND'
- Should system support multi-currency in future?

**Resolution:**
- For now: all prices in VND, store currency for audit trail
- Rounding always to 10,000 VND (local market rule)

---

### AMBIGUITY-003: Listing Linking
**Source:** Section 3, 4
**Issue:**
- Auto Pricing: can be called before/during listing creation
- Appraiser Pricing: must have existing listing
- But Listing entity not yet in codebase
- How to handle Auto Pricing for "new" products not yet listed?

**Resolution:**
- Auto Pricing API call doesn't require listingId - it's stateless calculation
- Store result if needed, but can be discarded
- Appraiser Pricing flow requires listingId (dependent on Listing entity)

---

## 8. Implementation Scope Assessment

### Phase 1: IMPLEMENTABLE NOW (Q4 2026)

| Feature | Depends On | Est. Effort |
|---|---|---|
| Reference Price Management (CRUD) | PostgreSQL, Category | 2 days |
| Auto Pricing Calculation | BigDecimal, Rounding | 2 days |
| Auto Pricing Controller | Spring MVC, Auth | 1 day |
| Database Migrations | PostgreSQL | 1 day |
| Automated Tests (Unit, Service) | JUnit, Mockito | 2 days |
| **Total Phase 1** | | **8 days** |

### Phase 2: BLOCKED (Waiting for Dependencies)

| Feature | Blocker | Notes |
|---|---|---|
| Appraiser Pricing Endpoints | Listing entity missing | Frontend can still call auto pricing |
| Commission Settlement | Order + Payment modules | Complex escrow logic |
| Appraiser Reputation Calc | Order completion flow | Requires transaction history |
| Appraiser Categories Mgmt | User role system expansion | APPRAISER role exists, but categories not mapped |
| Full Integration Tests | All modules | E2E testing blocked |

---

## 9. Dependency Chain Analysis

```
Pricing Engine
│
├─ Auto Pricing (READY)
│  └─ Reference Price Management
│     └─ PostgreSQL + Category entity ✓
│
└─ Appraiser Pricing (BLOCKED)
   ├─ Listing entity ✗
   ├─ Order + Payment modules ✗
   ├─ Notification (SQS/SNS) ✗
   ├─ Chat system ✗
   └─ Reputation system ✗
```

---

## 10. Summary Table

| ID | Requirement | Status | Priority | Blocker |
|---|---|---|---|---|
| REQ-001 | Auto Pricing Eligibility | TO_IMPLEMENT | HIGH | None |
| REQ-002 | Auto Pricing Inputs | TO_IMPLEMENT | HIGH | None |
| REQ-003 | Auto Pricing Calculation | TO_IMPLEMENT | HIGH | None |
| REQ-004 | Auto Pricing Output | TO_IMPLEMENT | HIGH | None |
| REQ-005 | Reference Price CRUD | TO_IMPLEMENT | HIGH | None |
| REQ-006 | Appraiser Eligibility | BLOCKED | HIGH | Role system |
| REQ-007 | Appraiser Flow | BLOCKED | HIGH | Listing entity |
| REQ-008 | Appraiser Rating | TODO | MEDIUM | Order module |
| REQ-009 | Commission Settlement | BLOCKED | MEDIUM | Escrow module |
| REQ-010 | Appraiser Display | TODO | MEDIUM | Reputation |
| REQ-API-001 | POST /api/pricing/auto | TO_IMPLEMENT | HIGH | None |
| REQ-API-002 to 006 | Appraiser endpoints | BLOCKED | HIGH | Listing |
| REQ-API-007 to 009 | Admin endpoints | TO_IMPLEMENT | HIGH | None |
| REQ-VAL-001 | Input Validation | TO_IMPLEMENT | HIGH | None |
| REQ-VAL-002 | Business Rule Validation | TO_IMPLEMENT | HIGH | None |
| REQ-DB-001 to 004 | Database tables | TO_IMPLEMENT | HIGH | None |

---

## 11. Recommendations

1. **Proceed with Phase 1**: Auto Pricing is fully implementable and provides immediate value
2. **Document Conflicts**: CONFLICT-001 (rounding) needs clarification from product owner
3. **Plan Phase 2**: Appraiser Pricing blocked; prioritize Listing entity implementation
4. **Test Thoroughly**: Boundary testing for deprecation/condition/warranty factors critical
5. **Monitor**: Track Phase 2 dependencies; can parallelize once Listing entity starts
