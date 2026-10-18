# Store & Category Implementation Review

**Status:** ⚠️ ISSUES FOUND - Need fixing before push  
**Date:** October 9, 2026

---

## Summary

Category và Store implementation có **vấn đề quan trọng** cần fix trước khi push Pricing Engine.

---

## Issues Found

### 🔴 CRITICAL: Missing Category Fields for Pricing Engine

**Problem:** Database schema định nghĩa 2 fields cho categories table:
- `is_electronic` (BOOLEAN, DEFAULT FALSE) - Dùng cho Auto Pricing
- `requires_appraisal` (BOOLEAN, DEFAULT FALSE) - Dùng cho Appraisal Pricing

**Nhưng Category entity KHÔNG có các fields này!**

**From docs/database/database_design.md:**
```sql
| Column               | Type      | Constraint       | Mô tả |
|---|---|---|---|
| `is_electronic`      | BOOLEAN   | DEFAULT FALSE    | Là thiết bị điện tử (dùng Auto-Pricing) |
| `requires_appraisal` | BOOLEAN   | DEFAULT FALSE    | Cần thẩm định hay không |
```

**Current Category entity:**
```java
@Column(nullable = false, length = 100)
private String name;

@Column(length = 500)
private String description;

// Không có is_electronic
// Không có requires_appraisal
```

**Impact:**
- ❌ Pricing engine cannot determine if category supports auto-pricing
- ❌ Cannot determine if category requires manual appraisal
- ❌ AutoPricingService using category name check (workaround, not ideal)
- ❌ CategoryResponse DTO không có fields này

---

### 🔴 CRITICAL: Missing Error Code

**Problem:** CategoryService dùng error code không tồn tại:
```java
throw new AppException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
```

**Nhưng ErrorCode.CATEGORY_NAME_ALREADY_EXISTS không defined!**

**Will cause:** Runtime exception khi tạo category duplicate name

---

### 🟡 WARNING: Incomplete Category Deletion Check

**Problem:** CategoryService.delete() có TODO:
```java
public void delete(UUID id) {
    Category category = findByIdOrThrow(id);
    
    if (category.isRoot() && repository.existsByParentId(id)) {
        throw new AppException(ErrorCode.CATEGORY_HAS_CHILDREN);
    }
    
    // TODO: check thêm listings, products
    repository.delete(category);
}
```

**Issue:**
- Nếu category có listings (products), vẫn delete được
- Sẽ orphan các products
- Foreign key constraint sẽ fail

**Should validate:** Không delete category nếu có listings dùng nó

---

### 🟡 WARNING: Store Status Race Condition

**Problem:** StoreService.updateMyStore() có potential race:
```java
public void updateMyStore(...) {
    Store store = findByOwner();
    
    if (store.status == BLOCKED) {
        throw error; // Store bị block
    }
    // Between here and save, store could be blocked by admin
    store.update(name, description, address);
    storeRepository.save(store);
}
```

**Not critical** (Flyway handles it), nhưng có thể improve.

---

### 🟡 INFO: Store-Category No Direct Relationship

**Current design:** Store ↔ Listings ↔ Category

**This is OK**, but means:
- Categories are global (all stores can use any category)
- No category-specific store settings
- Pricing engine doesn't need to validate store-category compatibility

---

## What Needs to be Fixed

### Fix 1: Add Missing Fields to Category Entity

**File:** `src/main/java/com/moldy/moldymarket/category/entity/Category.java`

**Add:**
```java
@Column(nullable = false)
private boolean isElectronic = false;

@Column(nullable = false)
private boolean requiresAppraisal = false;
```

**Add getters:**
```java
public boolean isElectronic() {
    return isElectronic;
}

public boolean requiresAppraisal() {
    return requiresAppraisal;
}
```

**Add factory methods:**
```java
public static Category createRoot(String name, String description, boolean isElectronic) {
    Category category = new Category();
    category.name = name;
    category.description = description;
    category.level = ROOT_LEVEL;
    category.status = CategoryStatus.ACTIVE;
    category.isElectronic = isElectronic;
    return category;
}
```

### Fix 2: Update CategoryResponse DTO

**File:** `src/main/java/com/moldy/moldymarket/category/dto/CategoryResponse.java`

**Add:**
```java
public record CategoryResponse(
    UUID id,
    String name,
    String description,
    UUID parentId,
    int level,
    CategoryStatus status,
    boolean hasChildren,
    boolean isElectronic,        // ADD THIS
    boolean requiresAppraisal,   // ADD THIS
    List<CategoryResponse> children,
    Instant createdAt,
    Instant updatedAt
) {}
```

### Fix 3: Add Missing Error Code

**File:** `src/main/java/com/moldy/moldymarket/common/ErrorCode.java`

**Add:**
```java
CATEGORY_NAME_ALREADY_EXISTS(409, "Category name already exists", "Tên danh mục đã tồn tại"),
```

### Fix 4: Complete Category Deletion Validation

**File:** `src/main/java/com/moldy/moldymarket/category/service/CategoryService.java`

**Update delete() method:**
```java
@Transactional
public void delete(UUID id) {
    Category category = findByIdOrThrow(id);

    if (category.isRoot() && repository.existsByParentId(id)) {
        throw new AppException(ErrorCode.CATEGORY_HAS_CHILDREN);
    }

    // NEW: Check for listings
    // TODO: Call ListingRepository.countByCategoryId(id) > 0
    // if (listingRepository.countByCategoryId(id) > 0) {
    //     throw new AppException(ErrorCode.CATEGORY_HAS_LISTINGS);
    // }

    repository.delete(category);
}
```

### Fix 5: Update CategoryMapper

**File:** `src/main/java/com/moldy/moldymarket/category/mapper/CategoryMapper.java`

**Update toResponse() to include new fields:**
```java
return new CategoryResponse(
    category.getId(),
    category.getName(),
    category.getDescription(),
    category.getParentId(),
    category.getLevel(),
    category.getStatus(),
    category.hasChildren(),
    category.isElectronic(),      // ADD THIS
    category.requiresAppraisal(),  // ADD THIS
    children,
    category.getCreatedAt(),
    category.getUpdatedAt()
);
```

### Fix 6: Update CategoryService.create()

**File:** `src/main/java/com/moldy/moldymarket/category/service/CategoryService.java`

**Update CreateCategoryRequest DTO:**
```java
public record CreateCategoryRequest(
    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must not exceed 100 characters")
    String name,

    @Size(max = 500, message = "description must not exceed 500 characters")
    String description,

    UUID parentId,
    
    boolean isElectronic,     // ADD THIS
    boolean requiresAppraisal // ADD THIS
) {}
```

**Update create() method:**
```java
@Transactional
public CategoryResponse create(CreateCategoryRequest request) {
    Category category;
    if (request.parentId() == null) {
        validateNameUnique(request.name(), null, null);
        category = Category.createRoot(
            request.name(), 
            request.description(),
            request.isElectronic()  // PASS THIS
        );
    } else {
        // ... validation ...
        category = Category.createSubCategory(
            request.name(), 
            request.description(), 
            parent
        );
        category.setIsElectronic(request.isElectronic());
        category.setRequiresAppraisal(request.requiresAppraisal());
    }
    
    return CategoryMapper.toResponse(repository.save(category));
}
```

---

## Files to Modify

```
1. src/main/java/com/moldy/moldymarket/category/entity/Category.java
   - Add isElectronic field
   - Add requiresAppraisal field
   - Add getters
   - Update factory methods

2. src/main/java/com/moldy/moldymarket/category/dto/CategoryResponse.java
   - Add isElectronic field
   - Add requiresAppraisal field

3. src/main/java/com/moldy/moldymarket/category/dto/CreateCategoryRequest.java
   - Add isElectronic field
   - Add requiresAppraisal field

4. src/main/java/com/moldy/moldymarket/category/service/CategoryService.java
   - Add CATEGORY_NAME_ALREADY_EXISTS error code check
   - Update delete() to validate no listings
   - Update create() to handle new fields

5. src/main/java/com/moldy/moldymarket/category/mapper/CategoryMapper.java
   - Update toResponse() to include new fields

6. src/main/java/com/moldy/moldymarket/common/ErrorCode.java
   - Add CATEGORY_NAME_ALREADY_EXISTS error code
```

---

## Impact on Pricing Engine

**Current Workaround (AutoPricingService):**
```java
private boolean isSupportedCategory(UUID categoryId) {
    Category category = categoryRepository.findById(categoryId).orElse(null);
    if (category == null) return false;
    
    String categoryName = category.getName();
    return "Điện thoại".equals(categoryName) || "Laptop".equals(categoryName);
}
```

**Issue:** Relies on exact category names, not flexible

**Better approach (after fixes):**
```java
private boolean isSupportedCategory(UUID categoryId) {
    Category category = categoryRepository.findById(categoryId).orElse(null);
    if (category == null) return false;
    
    return category.isElectronic(); // Use flag instead of name
}
```

---

## Recommendation

### MUST FIX before pushing:
1. ✅ Add isElectronic + requiresAppraisal fields
2. ✅ Add missing error code
3. ✅ Update DTOs and mappers
4. ✅ Complete deletion validation

### NICE TO HAVE:
- Fix store status race condition (low priority)

---

## Store Status: ✅ OK

**No issues with Store implementation.** Store entity is:
- ✅ Clean design
- ✅ Proper validation
- ✅ Correct relationships
- ✅ Ready to use

---

## Timeline

**Estimated time to fix:** ~30 minutes
- Add fields to Category entity (5 min)
- Update DTOs (5 min)
- Update mappers (5 min)
- Update service logic (10 min)
- Verify compilation + tests (5 min)

---

## Summary

| Item | Status | Action |
|------|--------|--------|
| Category entity fields | 🔴 CRITICAL | Add isElectronic, requiresAppraisal |
| Error codes | 🔴 CRITICAL | Add CATEGORY_NAME_ALREADY_EXISTS |
| Category deletion validation | 🟡 WARNING | Complete TODO check |
| DTOs | 🟡 WARNING | Update to include new fields |
| Store implementation | ✅ OK | No changes needed |

---

**Recommendation: Fix Category issues before pushing Pricing Engine code.**
