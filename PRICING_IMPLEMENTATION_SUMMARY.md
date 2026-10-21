# Pricing Engine Implementation Summary

**Status:** Need implementation of Appraiser endpoints (Auto Pricing done)

---

## Endpoints Theo Docs

| Endpoint | Method | Auth | Status | Notes |
|----------|--------|------|--------|-------|
| `/api/pricing/auto` | POST | USER | ✅ DONE | Auto Pricing calculation |
| `/api/pricing/appraiser/request` | POST | USER (seller) | ❌ TODO | Seller request appraiser |
| `/api/pricing/appraiser/{requestId}/submit` | POST | APPRAISER | ❌ TODO | Appraiser submit result |
| `/api/pricing/appraiser/{requestId}/accept` | POST | USER (seller) | ❌ TODO | Seller accept result |
| `/api/pricing/appraiser/{requestId}/reject` | POST | USER (seller) | ❌ TODO | Seller reject result |
| `/api/pricing/{listingId}` | GET | USER | ❌ TODO | Get pricing results for listing |
| `/api/admin/reference-prices` | GET | ADMIN | ✅ DONE | List reference prices |
| `/api/admin/reference-prices` | POST | ADMIN | ✅ DONE | Create reference price |
| `/api/admin/reference-prices/{id}` | PUT | ADMIN | ✅ DONE | Update reference price |

---

## Classes & Functions Tóm tắt

### 🔴 ENTITIES

#### 1. **ReferencePriceEntity**
- **Purpose:** Lưu giá tham chiếu cho models (điện thoại, laptop)
- **Key fields:**
  - `id` (UUID)
  - `categoryId` (UUID) - category của model
  - `brand` (String) - ví dụ "Apple", "Samsung"
  - `modelName` (String) - ví dụ "iPhone 15 Pro Max"
  - `referencePrice` (BigDecimal) - giá tham chiếu VND
  - `updatedAt` (Instant) - lần cập nhật cuối
- **Role:** Dùng bởi Auto Pricing để lấy giá tham chiếu

#### 2. **PricingRequestEntity**
- **Purpose:** Lưu log của mỗi request (auto hoặc appraiser)
- **Key fields:**
  - `id` (UUID)
  - `sellerId` (UUID) - người bán
  - `categoryId` (UUID)
  - `modelId` (UUID) - reference price ID
  - `pricingMode` (ENUM: AUTO / APPRAISER)
  - `status` (ENUM: SUGGESTED / MANUAL_REQUIRED / PENDING / SELLER_REJECTED / EXPIRED / APPRAISER_SUGGESTED)
  - Input fields: `origin`, `warrantyStatus`, `conditionLevel`, `usageMonths`
  - Output fields: `suggestedPrice`, `referencePrice`, deprecation/condition/warranty factors
  - Appraiser fields: `appraiserId`, `appraiserNote`, `commissionRate`, `rejectCount`
- **Role:** Central table cho cả AUTO và APPRAISER pricing

#### 3. **AppraiserProfileEntity**
- **Purpose:** Lưu thông tin người định giá
- **Key fields:**
  - `id` (UUID)
  - `userId` (UUID) - link tới User
  - `status` (ENUM: PENDING / ACTIVE / SUSPENDED)
  - `totalCompleted` (Integer) - số giao dịch hoàn thành
  - `accurateCount` (Integer) - số lần đề xuất nằm trong ±20%
- **Role:** Quản lý appraiser profile, điểm uy tín

#### 4. **AppraiserCategoryEntity**
- **Purpose:** Lưu mapping appraiser - categories (appraiser được duyệt định giá cho categories nào)
- **Key fields:**
  - `appraiserId` (UUID FK)
  - `categoryId` (UUID FK)
  - Composite PK: (appraiserId, categoryId)
- **Role:** Kiểm tra appraiser có được duyệt cho category không

#### 5. **Enum classes**
```java
PricingMode         : AUTO, APPRAISER
PricingStatus       : SUGGESTED, MANUAL_REQUIRED, PENDING, SELLER_REJECTED, EXPIRED, APPRAISER_SUGGESTED
ConditionLevel      : NEW(1.0), SCRATCHED(0.9), PARTIAL_DAMAGE(0.7), HEAVILY_DAMAGED(0.45)
WarrantyStatus      : UNDER_WARRANTY(1.05), EXPIRED_UNDER_1Y(0.97), EXPIRED_OVER_1Y(0.9)
AppraiserStatus     : PENDING, ACTIVE, SUSPENDED
```

---

### 🟢 SERVICES

#### 1. **DepreciationFactorService**
- **Method:** `calculateFactor(int usageMonths) → BigDecimal`
- **Purpose:** Tính hệ số khấu hao dựa trên số tháng sử dụng
- **Logic:**
  ```
  0-3 months    → 0.90
  4-6 months    → 0.82
  7-12 months   → 0.72
  13-18 months  → 0.63
  19-24 months  → 0.55
  25-36 months  → 0.45
  > 36 months   → 0.35
  ```
- **Used by:** AutoPricingService

#### 2. **PricingCalculationService**
- **Method:** `calculateSuggestedPrice(long refPrice, BigDecimal depFactor, BigDecimal condFactor, BigDecimal warFactor) → long`
- **Purpose:** Tính giá đề xuất theo công thức: refPrice × depFactor × condFactor × warFactor
- **Rounding:** Làm tròn đến 10,000 VND (HALF_UP)
- **Used by:** AutoPricingService

#### 3. **AutoPricingService** ✅ DONE
- **Method:** `calculatePrice(UUID sellerId, AutoPricingRequest request) → AutoPricingResponse`
- **Purpose:** Main Auto Pricing logic
- **Logic:**
  1. Validate category support (Điện thoại / Laptop)
  2. Validate origin = OFFICIAL_VN
  3. Check modelId exists in reference_prices
  4. If valid → calculate price, save to DB, return SUGGESTED
  5. If invalid → save to DB with MANUAL_REQUIRED, return reason
- **Used by:** AutoPricingController

#### 4. **ReferencePriceService** ✅ DONE
- **Methods:**
  - `createReferencePrice(CreateReferencePriceRequest) → ReferencePriceResponse`
  - `getReferencePrices(Pageable) → Page<ReferencePriceResponse>`
  - `getReferencePriceById(UUID) → ReferencePriceResponse`
  - `updateReferencePrice(UUID, BigDecimal newPrice) → ReferencePriceResponse`
  - `deleteReferencePrice(UUID) → void`
- **Purpose:** CRUD operations for reference prices (admin only)
- **Used by:** ReferencePriceController

#### 5. **AppraiserPricingService** ❌ TODO
- **Methods needed:**
  - `requestAppraiserPricing(UUID sellerId, UUID listingId, UUID appraiserId, BigDecimal commissionRate) → AppraiserPricingResponse`
  - `submitAppraiserPricing(UUID requestId, long suggestedPrice, String note) → AppraiserPricingResponse`
  - `acceptAppraiserPricing(UUID requestId) → AppraiserPricingResponse`
  - `rejectAppraiserPricing(UUID requestId) → AppraiserPricingResponse`
- **Purpose:** Handle appraiser pricing workflow
- **Logic:**
  - Request: Create pricing_request with status=PENDING
  - Submit: Check if requestId exists, update with suggestedPrice, note, status=APPRAISER_SUGGESTED
  - Accept: Mark as accepted, track for accuracy calculation later
  - Reject: Increment rejectCount, if >= 3 reject by requestId

#### 6. **AppraiserProfileService** ❌ TODO
- **Methods needed:**
  - `createAppraiserProfile(UUID userId) → AppraiserProfileResponse`
  - `getAppraiserProfile(UUID userId) → AppraiserProfileResponse`
  - `approveAppraiserForCategory(UUID appraiserId, UUID categoryId) → void`
  - `getAppraisersByCategory(UUID categoryId) → List<AppraiserProfileResponse>`
  - `calculateAccuracyScore(UUID appraiserId) → BigDecimal`
- **Purpose:** Manage appraiser profiles and permissions
- **Logic:**
  - Create with status=PENDING
  - Admin approves → add to appraiser_categories table
  - Calculate accuracy: (accurateCount / totalCompleted) × 100

---

### 🟡 CONTROLLERS

#### 1. **AutoPricingController** ✅ DONE
```
POST /api/pricing/auto
├─ Auth: @IsUser
├─ Request: AutoPricingRequest
│  └─ categoryId, modelId, origin, warrantyStatus, conditionLevel, usageMonths
├─ Response: 200 OK
│  ├─ success case: AutoPricingResponse with SUGGESTED
│  └─ no support: AutoPricingResponse with MANUAL_REQUIRED + reason
└─ Calls: AutoPricingService.calculatePrice()
```

#### 2. **ReferencePriceController** ✅ DONE
```
GET    /api/admin/reference-prices         (paginated list)
POST   /api/admin/reference-prices         (create)
GET    /api/admin/reference-prices/{id}    (get single)
PUT    /api/admin/reference-prices/{id}    (update)
DELETE /api/admin/reference-prices/{id}    (delete)

All with @IsAdmin
All call: ReferencePriceService methods
```

#### 3. **AppraiserPricingController** ❌ TODO
```
POST /api/pricing/appraiser/request
├─ Auth: @IsUser (seller)
├─ Request: {listingId, appraiserId, commissionRate}
├─ Response: 201 CREATED with requestId, status=PENDING
└─ Calls: AppraiserPricingService.requestAppraiserPricing()

POST /api/pricing/appraiser/{requestId}/submit
├─ Auth: @IsAppraiser
├─ Request: {suggestedPrice, note}
├─ Response: 200 OK with status=APPRAISER_SUGGESTED
└─ Calls: AppraiserPricingService.submitAppraiserPricing()

POST /api/pricing/appraiser/{requestId}/accept
├─ Auth: @IsUser (seller)
├─ Response: 200 OK
└─ Calls: AppraiserPricingService.acceptAppraiserPricing()

POST /api/pricing/appraiser/{requestId}/reject
├─ Auth: @IsUser (seller)
├─ Response: 200 OK
└─ Calls: AppraiserPricingService.rejectAppraiserPricing()

GET /api/pricing/{listingId}
├─ Auth: @IsUser
├─ Response: All pricing results (auto + appraiser) for listing
└─ Calls: PricingRequestRepository.findByListingId()
```

#### 4. **AppraiserProfileController** ❌ TODO
```
GET    /api/appraiser/profile              (get own profile)
POST   /api/admin/appraiser/{id}/approve   (admin approve)
GET    /api/admin/appraisers               (list appraisers for admin)
PUT    /api/admin/appraiser/{id}/status    (change status)
GET    /api/admin/appraiser/{id}/score     (view accuracy score)

With appropriate auth
Calls: AppraiserProfileService methods
```

---

### 🟡 DTOs

#### Auto Pricing DTOs ✅
```java
AutoPricingRequest {
  categoryId: UUID
  modelId: UUID
  origin: String (OFFICIAL_VN)
  warrantyStatus: WarrantyStatus
  conditionLevel: ConditionLevel
  usageMonths: int
}

AutoPricingResponse {
  pricingMode: PricingMode (AUTO)
  status: PricingStatus (SUGGESTED or MANUAL_REQUIRED)
  suggestedPrice: Long (nullable if MANUAL_REQUIRED)
  referencePrice: Long
  deprecationFactor: BigDecimal
  conditionFactor: BigDecimal
  warrantyFactor: BigDecimal
  referenceModelName: String
  referencePriceUpdatedAt: Instant
  reason: String (nullable, only if MANUAL_REQUIRED)
  disclaimer: String
}
```

#### Reference Price DTOs ✅
```java
ReferencePriceRequest {
  categoryId: UUID
  brand: String
  modelName: String
  referencePrice: BigDecimal
}

ReferencePriceResponse {
  id: UUID
  categoryId: UUID
  brand: String
  modelName: String
  referencePrice: BigDecimal
  updatedAt: Instant
}
```

#### Appraiser DTOs ❌ TODO
```java
AppraiserRequestRequest {
  listingId: UUID
  appraiserId: UUID
  commissionRate: BigDecimal (%)
}

AppraiserSubmitRequest {
  suggestedPrice: Long
  note: String
}

AppraiserPricingResponse {
  requestId: UUID
  status: PricingStatus
  appraiserId: UUID
  appraiserName: String
  suggestedPrice: Long
  note: String
  commissionRate: BigDecimal
  expiredAt: Instant (for PENDING requests)
}

AppraiserProfileResponse {
  userId: UUID
  userName: String
  status: AppraiserStatus
  totalCompleted: int
  accurateCount: int
  accuracyScore: BigDecimal (%)
  approvedCategories: List<UUID>
}
```

---

### 🟡 Repositories

#### Current ✅
- `ReferencePriceRepository`
- `PricingRequestRepository`
- `AppraiserProfileRepository`
- `AppraiserCategoryRepository`

#### Methods to add ❌
```java
// PricingRequestRepository
findByListingId(UUID listingId)
findBySellerIdAndStatus(UUID sellerId, PricingStatus status)
findByAppraiserIdAndStatus(UUID appraiserId, PricingStatus status)
countByAppraiserId(UUID appraiserId)
countByAppraiserIdAndStatus(UUID appraiserId, String statusContainingAccurate)

// AppraiserProfileRepository
findByUserId(UUID userId)
findAllByStatus(AppraiserStatus status)

// AppraiserCategoryRepository
findByAppraiserId(UUID appraiserId)
findByAppraiserId(UUID appraiserId, UUID categoryId)
existsByAppraiserIdAndCategoryId(UUID appraiserId, UUID categoryId)
```

---

## Implementation Priority

### Phase 1 ✅ (DONE)
- Auto Pricing endpoints
- Reference Price CRUD endpoints
- Basic entities and enums

### Phase 2 ❌ (TODO)
1. AppraiserPricingService + controller
   - Request appraiser
   - Submit result
   - Accept/reject
   - Get pricing history per listing
2. AppraiserProfileService + controller
   - Create profile
   - Approve for categories
   - View accuracy score
3. DTOs for appraiser operations
4. Repository methods for queries

---

## Key Business Rules to Implement

1. **Auto Pricing validation:**
   - Category must be Điện thoại or Laptop
   - Origin must be OFFICIAL_VN
   - ModelId must exist in reference_prices

2. **Appraiser request:**
   - Appraiser must have status=ACTIVE
   - Appraiser must be approved for category
   - Seller can have max 3 rejections per listing

3. **Appraiser submit:**
   - Status must be PENDING
   - Submitter must match appraiserId

4. **Accept/Reject:**
   - Only seller can accept/reject
   - Reject increments rejectCount (max 3)

5. **Accuracy calculation:**
   - Only count if transaction successful
   - Only count if price within ±20% of actual sale price
   - Only display score if totalCompleted >= 10
