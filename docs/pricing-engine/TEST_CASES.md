# Pricing Engine - Test Cases

**Created:** 2026-10-09  
**Last Updated:** 2026-10-09

---

## Test Case Organization

Test cases are organized by feature area:
1. **Auto Pricing** - POST /api/pricing/auto endpoint
2. **Reference Price Management** - GET/POST/PUT /api/admin/reference-prices endpoints
3. **Business Rule Validation** - Deprecation/condition/warranty factor calculations
4. **Error Handling** - Input validation, error codes
5. **Authorization** - Role-based access control
6. **Boundary Testing** - Edge cases for factor calculations

---

## Auto Pricing Tests

### Category: Happy Path

#### TC-AUTO-001: Basic Auto Pricing - All Factors at Neutral
**Requirement:** REQ-001, REQ-002, REQ-003, REQ-004
**Objective:** Verify basic price calculation with standard inputs
**Preconditions:**
- Reference price exists for model: iPhone 15 Pro Max = 28,000,000 VND
- Category ID: 00000000-0000-0001-0000-000000000001 (Phone)
- Model ID: reference_prices record ID for iPhone 15 Pro Max

**Request:**
```json
{
  "categoryId": "00000000-0000-0001-0000-000000000001",
  "modelId": "{model_id_iphone_15_pro_max}",
  "origin": "OFFICIAL_VN",
  "warrantyStatus": "EXPIRED_UNDER_1Y",
  "conditionLevel": "SCRATCHED",
  "usageMonths": 10
}
```

**Expected Response (HTTP 200):**
```json
{
  "success": true,
  "data": {
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
}
```

**Pass Criteria:**
- HTTP 200 OK
- Status = SUGGESTED
- Calculation correct: 28,000,000 × 0.72 × 0.90 × 0.97 = 17,599,680 → round to 17,600,000
- All factors correct
- Disclaimer present

**Notes:** Documentation example: 17,590,000 but calculation should yield 17,600,000. Assuming rounding rule is "to nearest 10,000 VND".

---

#### TC-AUTO-002: New Condition with Warranty
**Requirement:** REQ-003
**Objective:** Test with NEW condition and UNDER_WARRANTY
**Request Variation:**
```json
{
  "categoryId": "00000000-0000-0001-0000-000000000001",
  "modelId": "{model_id_iphone_15_pro_max}",
  "origin": "OFFICIAL_VN",
  "warrantyStatus": "UNDER_WARRANTY",
  "conditionLevel": "NEW",
  "usageMonths": 0
}
```

**Expected Calculation:**
- Deprecation (0 months): 0.90
- Condition (NEW): 1.00
- Warranty (UNDER_WARRANTY): 1.05
- Result: 28,000,000 × 0.90 × 1.00 × 1.05 = 26,460,000

**Pass Criteria:**
- suggestedPrice = 26,460,000
- All factors correct

---

#### TC-AUTO-003: Heavily Damaged, No Warranty
**Requirement:** REQ-003
**Objective:** Test worst condition scenario
**Request Variation:**
```json
{
  "conditionLevel": "HEAVILY_DAMAGED",
  "warrantyStatus": "EXPIRED_OVER_1Y",
  "usageMonths": 48
}
```

**Expected Calculation:**
- Deprecation (48 months, > 36): 0.35
- Condition (HEAVILY_DAMAGED): 0.45
- Warranty (EXPIRED_OVER_1Y): 0.90
- Result: 28,000,000 × 0.35 × 0.45 × 0.90 = 3,969,000

**Pass Criteria:**
- suggestedPrice = 3,969,000
- Status = SUGGESTED

---

### Category: Deprecation Factor Boundaries

These tests verify correct deprecation factor selection at boundary values.

#### TC-AUTO-DBG-001: Deprecation Boundary - 0 months (0.90)
- usageMonths: 0
- Expected factor: 0.90

#### TC-AUTO-DBG-002: Deprecation Boundary - 3 months (0.90)
- usageMonths: 3
- Expected factor: 0.90

#### TC-AUTO-DBG-003: Deprecation Boundary - 4 months (0.82)
- usageMonths: 4
- Expected factor: 0.82

#### TC-AUTO-DBG-004: Deprecation Boundary - 6 months (0.82)
- usageMonths: 6
- Expected factor: 0.82

#### TC-AUTO-DBG-005: Deprecation Boundary - 7 months (0.72)
- usageMonths: 7
- Expected factor: 0.72

#### TC-AUTO-DBG-006: Deprecation Boundary - 12 months (0.72)
- usageMonths: 12
- Expected factor: 0.72

#### TC-AUTO-DBG-007: Deprecation Boundary - 13 months (0.63)
- usageMonths: 13
- Expected factor: 0.63

#### TC-AUTO-DBG-008: Deprecation Boundary - 18 months (0.63)
- usageMonths: 18
- Expected factor: 0.63

#### TC-AUTO-DBG-009: Deprecation Boundary - 19 months (0.55)
- usageMonths: 19
- Expected factor: 0.55

#### TC-AUTO-DBG-010: Deprecation Boundary - 24 months (0.55)
- usageMonths: 24
- Expected factor: 0.55

#### TC-AUTO-DBG-011: Deprecation Boundary - 25 months (0.45)
- usageMonths: 25
- Expected factor: 0.45

#### TC-AUTO-DBG-012: Deprecation Boundary - 36 months (0.45)
- usageMonths: 36
- Expected factor: 0.45

#### TC-AUTO-DBG-013: Deprecation Boundary - 37 months (0.35)
- usageMonths: 37
- Expected factor: 0.35

#### TC-AUTO-DBG-014: Deprecation Boundary - 60 months (0.35)
- usageMonths: 60
- Expected factor: 0.35

---

### Category: All Condition Factor Combinations

#### TC-AUTO-COND-001: Condition = NEW (1.00)
- conditionLevel: NEW
- Expected factor: 1.00

#### TC-AUTO-COND-002: Condition = SCRATCHED (0.90)
- conditionLevel: SCRATCHED
- Expected factor: 0.90

#### TC-AUTO-COND-003: Condition = PARTIAL_DAMAGE (0.70)
- conditionLevel: PARTIAL_DAMAGE
- Expected factor: 0.70

#### TC-AUTO-COND-004: Condition = HEAVILY_DAMAGED (0.45)
- conditionLevel: HEAVILY_DAMAGED
- Expected factor: 0.45

---

### Category: All Warranty Factor Combinations

#### TC-AUTO-WARR-001: Warranty = UNDER_WARRANTY (1.05)
- warrantyStatus: UNDER_WARRANTY
- Expected factor: 1.05

#### TC-AUTO-WARR-002: Warranty = EXPIRED_UNDER_1Y (0.97)
- warrantyStatus: EXPIRED_UNDER_1Y
- Expected factor: 0.97

#### TC-AUTO-WARR-003: Warranty = EXPIRED_OVER_1Y (0.90)
- warrantyStatus: EXPIRED_OVER_1Y
- Expected factor: 0.90

---

### Category: Validation & Error Cases

#### TC-AUTO-VAL-001: Missing categoryId
**Request:** Omit categoryId field
**Expected Response:** HTTP 400 Bad Request
**Expected Error Message:** "categoryId is required"

#### TC-AUTO-VAL-002: Missing modelId
**Request:** Omit modelId field
**Expected Response:** HTTP 400 Bad Request

#### TC-AUTO-VAL-003: Invalid origin (not OFFICIAL_VN)
**Request:** origin: "GREY_MARKET"
**Expected Response:** HTTP 200 with status = MANUAL_REQUIRED, reason = "ORIGIN_NOT_SUPPORTED"

#### TC-AUTO-VAL-004: Unsupported category
**Request:** categoryId = category for furniture (not phone/laptop)
**Expected Response:** HTTP 200 with status = MANUAL_REQUIRED, reason = "CATEGORY_NOT_SUPPORTED"

#### TC-AUTO-VAL-005: Model not found
**Request:** modelId = non-existent UUID
**Expected Response:** HTTP 200 with status = MANUAL_REQUIRED, reason = "MODEL_NOT_FOUND"

#### TC-AUTO-VAL-006: Negative usageMonths
**Request:** usageMonths: -1
**Expected Response:** HTTP 400 Bad Request
**Expected Message:** "usageMonths must be >= 0"

#### TC-AUTO-VAL-007: Null usageMonths
**Request:** Omit usageMonths
**Expected Response:** HTTP 400 Bad Request

---

### Category: Rounding Tests

#### TC-AUTO-ROUND-001: Rounding up (X.5+)
**Setup:** Calculate to get 17,599,680
**Expected:** Round to 17,600,000

#### TC-AUTO-ROUND-002: Rounding down (X.4-)
**Setup:** Craft inputs to get result like 17,544,000
**Expected:** Round to 17,540,000

#### TC-AUTO-ROUND-003: Exact ten-thousand
**Setup:** Get exactly 17,000,000
**Expected:** 17,000,000 (no change)

---

### Category: Authorization

#### TC-AUTO-AUTH-001: Unauthorized access (no token)
**Request:** POST /api/pricing/auto without Bearer token
**Expected Response:** HTTP 401 Unauthorized

#### TC-AUTO-AUTH-002: Valid user token
**Request:** POST /api/pricing/auto with valid user token
**Expected Response:** HTTP 200 (processing as normal)

---

## Reference Price Management Tests

### Category: CRUD Happy Path

#### TC-REF-001: Create reference price
**Request:**
```json
{
  "categoryId": "00000000-0000-0001-0000-000000000001",
  "brand": "Apple",
  "modelName": "iPhone 15 Pro Max",
  "referencePrice": 28000000
}
```

**Expected Response (HTTP 201):**
- id: UUID (generated)
- brand: "Apple"
- modelName: "iPhone 15 Pro Max"
- referencePrice: 28000000
- currency: "VND"
- createdAt: ISO 8601 timestamp

**Pass Criteria:** HTTP 201, all fields correct, unique constraint enforced

---

#### TC-REF-002: Get all reference prices (pagination)
**Request:** GET /api/admin/reference-prices?page=0&size=10

**Expected Response (HTTP 200):**
- Page object with content array
- Each item contains: id, categoryId, brand, modelName, referencePrice, etc.

---

#### TC-REF-003: Get single reference price
**Request:** GET /api/admin/reference-prices/{id}

**Expected Response (HTTP 200):**
- Single ReferencePriceResponse object

---

#### TC-REF-004: Update reference price
**Request:** PUT /api/admin/reference-prices/{id}
```json
{
  "categoryId": "00000000-0000-0001-0000-000000000001",
  "brand": "Apple",
  "modelName": "iPhone 15 Pro Max",
  "referencePrice": 27000000
}
```

**Expected Response (HTTP 200):**
- referencePrice: 27000000 (updated)
- updatedAt: current timestamp

---

#### TC-REF-005: Delete reference price
**Request:** DELETE /api/admin/reference-prices/{id}

**Expected Response:** HTTP 204 No Content

---

### Category: Validation

#### TC-REF-VAL-001: Missing categoryId
**Expected:** HTTP 400, "categoryId is required"

#### TC-REF-VAL-002: Empty brand
**Request:** brand: ""
**Expected:** HTTP 400, "brand is required"

#### TC-REF-VAL-003: Brand too long (>100 chars)
**Request:** brand: "A" × 101
**Expected:** HTTP 400, "brand must be 1-100 characters"

#### TC-REF-VAL-004: modelName too long (>200 chars)
**Request:** modelName: "A" × 201
**Expected:** HTTP 400, "modelName must be 1-200 characters"

#### TC-REF-VAL-005: Negative referencePrice
**Request:** referencePrice: -1000
**Expected:** HTTP 400, "referencePrice must be positive"

#### TC-REF-VAL-006: Zero referencePrice
**Request:** referencePrice: 0
**Expected:** HTTP 400, "referencePrice must be positive"

#### TC-REF-VAL-007: Duplicate brand+modelName
**Setup:** Create reference price for Apple iPhone 15
**Request:** Create another with same brand and model name
**Expected:** HTTP 400, "Reference price already exists for brand=..., model=..."

---

### Category: Authorization

#### TC-REF-AUTH-001: Non-admin creates reference price
**Request:** POST /api/admin/reference-prices with USER token
**Expected Response:** HTTP 403 Forbidden

#### TC-REF-AUTH-002: Admin creates reference price
**Request:** POST /api/admin/reference-prices with ADMIN token
**Expected Response:** HTTP 201

---

## Calculation Accuracy Tests

### Category: Formula Verification

#### TC-CALC-001: Exact calculation example from docs
**Reference:** Section 3.4 example
**Given:**
- referencePrice: 28,000,000
- deprecationFactor: 0.72
- conditionFactor: 0.90
- warrantyFactor: 0.97

**Manual Calculation:**
- 28,000,000 × 0.72 = 20,160,000
- 20,160,000 × 0.90 = 18,144,000
- 18,144,000 × 0.97 = 17,599,680
- Round to 10,000: 17,600,000

**Expected Result:** 17,600,000

---

#### TC-CALC-002: High precision - no rounding loss
**Given:**
- referencePrice: 10,000,000
- deprecationFactor: 0.90
- conditionFactor: 0.90
- warrantyFactor: 0.90

**Expected Result:**
- 10,000,000 × 0.90 × 0.90 × 0.90 = 7,290,000

---

## Integration Tests (Database Level)

#### TC-INT-001: Auto Pricing request saved to database
**After:** TC-AUTO-001 completes
**Verify:**
- pricing_requests table has 1 new row
- Fields: pricingMode=AUTO, status=SUGGESTED, modelId, conditionLevel, etc.

#### TC-INT-002: Reference price persists across multiple calls
**Setup:** Create reference price
**Call:** Auto Pricing API 3 times with same model
**Verify:** All 3 calls use same reference price from database

---

## Notes on Blocked/Not Applicable Tests

### Appraiser Pricing Tests (BLOCKED)
The following tests CANNOT be executed in current implementation:
- All Appraiser Pricing endpoints (POST /api/pricing/appraiser/request, etc.)
- Appraiser profile approval flow
- Commission calculation
- Reputation scoring

**Reason:** Listing entity not yet implemented in codebase.

### Integration with Order/Payment (TODO)
- Commission settlement validation
- Escrow integration tests

---

## Test Execution Environment

**Framework:** JUnit 5 + Spring Boot Test
**Tools:** MockMvc for controller tests, Testcontainers for database (optional)
**Postman:** Newman CLI for API-level testing

**Seed Data Required:**
- Categories (from V2__seed_categories.sql)
- At least 2-3 reference prices for testing models
- Test user accounts with appropriate roles

---

## Test Coverage Summary

| Feature | Happy Path | Boundary | Validation | Authorization | Integration | Total |
|---------|-----------|----------|-----------|---------------|-------------|-------|
| Auto Pricing | 3 | 14 | 6 | 2 | 1 | 26 |
| Reference Prices | 5 | 0 | 7 | 2 | 1 | 15 |
| Calculations | 2 | 0 | 0 | 0 | 0 | 2 |
| **TOTAL** | **10** | **14** | **13** | **4** | **2** | **43** |

---

## Test Execution Status Template

For each test case execution:

| TC-ID | Requirement | Objective | Status | Result | Notes |
|-------|-------------|-----------|--------|--------|-------|
| TC-AUTO-001 | REQ-001 to 004 | Basic calculation | PASS/FAIL/BLOCKED/NOT RUN | [result] | [notes] |
