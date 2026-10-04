# Category Management

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Cấu trúc danh mục](#2-cấu-trúc-danh-mục)
3. [Database Design](#3-database-design)
4. [API Specification](#4-api-specification)
5. [Business Rules & Validation](#5-business-rules--validation)
6. [Product Integration](#6-product-integration)
7. [Test Checklist](#7-test-checklist)

---

## 1. Tổng quan

Category phục vụ 4 mục đích trong scope hiện tại:

- Phân loại listing khi người dùng đăng bán.
- Filter/search sản phẩm theo nhóm.
- Admin CRUD quản lý danh mục.
- Appraiser và pricing engine nhận biết sản phẩm thuộc nhóm nào.

Mô hình **2 cấp**, Adjacency List:

```
Level 1 — Root Category   (parentId = null)
    └── Level 2 — Sub-category   (parentId = <root id>)
```

- `parentId = null` → Root Category.
- `parentId = <uuid>` → Sub-category, parent phải là Root (level 1).
- Level 3 bị reject tại service — không triển khai, không cần.

---

## 2. Cấu trúc danh mục

```
Đồ điện tử
├── Điện thoại
├── Laptop
├── Máy tính bảng
├── Tai nghe
└── Phụ kiện điện tử

Đồ nội thất
├── Bàn
├── Ghế
├── Tủ
├── Giường
└── Đèn

Thời trang
├── Áo
├── Quần
├── Giày
├── Túi xách
└── Phụ kiện

Sách
├── Văn học
├── Kinh tế
├── Khoa học
├── Công nghệ
└── Giáo dục
```

---

## 3. Database Design

### Schema

```sql
CREATE TABLE categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    parent_id   UUID         REFERENCES categories(id) ON DELETE RESTRICT,
    level       SMALLINT     NOT NULL CHECK (level IN (1, 2)),
    status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_name_per_parent UNIQUE (parent_id, name)
);

CREATE INDEX idx_categories_parent_id ON categories(parent_id);
CREATE INDEX idx_categories_status    ON categories(status);
```

### Fields

| Field | Kiểu | Mô tả |
|---|---|---|
| `id` | UUID | Auto-generated |
| `name` | VARCHAR(100) | Unique trong cùng parent |
| `description` | VARCHAR(500) | Nullable |
| `parent_id` | UUID | `null` = Root; `uuid` = Sub-category |
| `level` | SMALLINT | `1` = Root, `2` = Sub-category |
| `status` | VARCHAR(10) | `ACTIVE` / `INACTIVE` |
| `created_at` | TIMESTAMPTZ | Set bởi `@PrePersist`, không update |
| `updated_at` | TIMESTAMPTZ | Set bởi `@PrePersist`, `@PreUpdate` |

### Seed data

```sql
INSERT INTO categories (id, name, parent_id, level) VALUES
  ('00000000-0000-0000-0000-000000000001', 'Đồ điện tử',  NULL, 1),
  ('00000000-0000-0000-0000-000000000002', 'Đồ nội thất', NULL, 1),
  ('00000000-0000-0000-0000-000000000003', 'Thời trang',  NULL, 1),
  ('00000000-0000-0000-0000-000000000004', 'Sách',        NULL, 1);

INSERT INTO categories (name, parent_id, level) VALUES
  ('Điện thoại',       '00000000-0000-0000-0000-000000000001', 2),
  ('Laptop',           '00000000-0000-0000-0000-000000000001', 2),
  ('Máy tính bảng',    '00000000-0000-0000-0000-000000000001', 2),
  ('Tai nghe',         '00000000-0000-0000-0000-000000000001', 2),
  ('Phụ kiện điện tử', '00000000-0000-0000-0000-000000000001', 2),
  ('Bàn',    '00000000-0000-0000-0000-000000000002', 2),
  ('Ghế',    '00000000-0000-0000-0000-000000000002', 2),
  ('Tủ',     '00000000-0000-0000-0000-000000000002', 2),
  ('Giường', '00000000-0000-0000-0000-000000000002', 2),
  ('Đèn',    '00000000-0000-0000-0000-000000000002', 2),
  ('Áo',       '00000000-0000-0000-0000-000000000003', 2),
  ('Quần',     '00000000-0000-0000-0000-000000000003', 2),
  ('Giày',     '00000000-0000-0000-0000-000000000003', 2),
  ('Túi xách', '00000000-0000-0000-0000-000000000003', 2),
  ('Phụ kiện', '00000000-0000-0000-0000-000000000003', 2),
  ('Văn học',   '00000000-0000-0000-0000-000000000004', 2),
  ('Kinh tế',   '00000000-0000-0000-0000-000000000004', 2),
  ('Khoa học',  '00000000-0000-0000-0000-000000000004', 2),
  ('Công nghệ', '00000000-0000-0000-0000-000000000004', 2),
  ('Giáo dục',  '00000000-0000-0000-0000-000000000004', 2);
```

---

## 4. API Specification

Base URL: `/api`

Response: `ApiResponse<T>` — `{ success, code, message, data, timestamp }`

### Danh sách endpoints

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| `GET` | `/api/categories` | Public | Cây danh mục ACTIVE |
| `GET` | `/api/categories/{id}` | Public | Chi tiết một category |
| `POST` | `/api/admin/categories` | ADMIN | Tạo category |
| `PUT` | `/api/admin/categories/{id}` | ADMIN | Cập nhật name / description |
| `DELETE` | `/api/admin/categories/{id}` | ADMIN | Xóa category |

---

### GET /api/categories

Trả toàn bộ cây ACTIVE — 1 DB query, build tree in-memory.
Root có `children[]` là Sub-categories ACTIVE. Category INACTIVE không xuất hiện.
`createdAt` và `updatedAt` luôn `null` trong response này.

**Response 200**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request completed successfully",
  "data": [
    {
      "id": "00000000-0000-0000-0000-000000000001",
      "name": "Đồ điện tử",
      "description": null,
      "parentId": null,
      "level": 1,
      "status": "ACTIVE",
      "hasChildren": true,
      "children": [
        {
          "id": "uuid-laptop",
          "name": "Laptop",
          "description": null,
          "parentId": "00000000-0000-0000-0000-000000000001",
          "level": 2,
          "status": "ACTIVE",
          "hasChildren": false,
          "children": [],
          "createdAt": null,
          "updatedAt": null
        }
      ],
      "createdAt": null,
      "updatedAt": null
    }
  ],
  "timestamp": "2026-10-01T10:00:00Z"
}
```

---

### GET /api/categories/{id}

Chi tiết một category ACTIVE. `children = null`. Có `createdAt` và `updatedAt`.

**Response 200**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request completed successfully",
  "data": {
    "id": "uuid-laptop",
    "name": "Laptop",
    "description": "Laptop các loại",
    "parentId": "00000000-0000-0000-0000-000000000001",
    "level": 2,
    "status": "ACTIVE",
    "hasChildren": false,
    "children": null,
    "createdAt": "2026-01-01T00:00:00Z",
    "updatedAt": "2026-01-01T00:00:00Z"
  },
  "timestamp": "2026-10-01T10:00:00Z"
}
```

**Lỗi**

| HTTP | Code | Khi nào |
|---|---|---|
| 404 | `CAT_001` | id không tồn tại hoặc INACTIVE |

---

### POST /api/admin/categories

Tạo Root (`parentId = null`) hoặc Sub-category (`parentId = <root uuid>`).

**Request body**

```json
{
  "name": "Laptop",
  "description": "Laptop các loại",
  "parentId": "00000000-0000-0000-0000-000000000001"
}
```

| Field | Bắt buộc | Ràng buộc |
|---|---|---|
| `name` | Có | 1–100 ký tự, unique trong cùng parent |
| `description` | Không | Tối đa 500 ký tự |
| `parentId` | Không | Nếu có: phải là Root (level 1) |

**Response 201** — trả `CategoryResponse` đầy đủ kèm `createdAt`, `updatedAt`.

**Lỗi**

| HTTP | Code | Khi nào |
|---|---|---|
| 400 | `COMMON_001` | `name` trống hoặc vượt 100 ký tự |
| 400 | `CAT_006` | `parentId` trỏ đến Sub-category (level 2) |
| 404 | `CAT_001` | `parentId` không tồn tại |
| 409 | `CAT_007` | `name` đã tồn tại trong cùng parent |

---

### PUT /api/admin/categories/{id}

Cập nhật `name` và `description`. Không thay đổi `parent` hay `level`.

**Request body**

```json
{
  "name": "Laptop Gaming",
  "description": "Laptop hiệu năng cao"
}
```

**Response 200** — trả `CategoryResponse` đã cập nhật, `updatedAt` thay đổi.

**Lỗi**

| HTTP | Code | Khi nào |
|---|---|---|
| 400 | `COMMON_001` | `name` trống hoặc vượt 100 ký tự |
| 404 | `CAT_001` | id không tồn tại |
| 409 | `CAT_007` | `name` mới trùng với sibling trong cùng parent |

---

### DELETE /api/admin/categories/{id}

Xóa vĩnh viễn. Guard hiện tại:

- **Root**: không xóa được nếu còn Sub-category con → `CAT_002`.
- **Sub-category**: xóa tự do *(guard listing chưa active, sẽ bổ sung khi Product module sẵn sàng)*.

**Response 204** — No Content.

**Lỗi**

| HTTP | Code | Khi nào |
|---|---|---|
| 404 | `CAT_001` | id không tồn tại |
| 409 | `CAT_002` | Root còn Sub-category con |

---

## 5. Business Rules & Validation

### Tạo

| Rule | Code | Chi tiết |
|---|---|---|
| Chỉ ADMIN | — | `@IsAdmin` trên tất cả `/api/admin/**` |
| Parent phải là Root | `CAT_006` | Kiểm tra `!parent.isRoot()` trong service |
| Name unique per parent | `CAT_007` | DB constraint + service validate |

### Cập nhật

Chỉ `name` và `description`. `parent` và `level` không thay đổi qua PUT.

### Xóa

| Loại | Guard hiện tại | Ghi chú |
|---|---|---|
| Root | `existsByParentId(id)` → `CAT_002` nếu còn Sub-category | Active |
| Sub-category | Không có guard | Guard listing sẽ bổ sung sau |

### Khi đăng bán sản phẩm

`CategoryService.validateProductCategory(categoryId)` được gọi từ Product module:

| Thứ tự | Check | Lỗi |
|---|---|---|
| 1 | Category tồn tại | 404 `CAT_001` |
| 2 | `isActive()` | 422 `CAT_004` |
| 3 | `isSubCategory()` | 422 `CAT_005` |

---

## 6. Product Integration

**Gọi từ ProductService trước khi tạo/cập nhật sản phẩm:**

```java
categoryService.validateProductCategory(categoryId);
```

**Foreign key:**

```sql
ALTER TABLE products
    ADD COLUMN category_id UUID NOT NULL REFERENCES categories(id);
```

**Query sản phẩm theo Root** (không cần recursive vì chỉ 2 cấp):

```sql
SELECT p.* FROM products p
JOIN categories c ON p.category_id = c.id
WHERE (c.id = :rootId OR c.parent_id = :rootId)
  AND c.status = 'ACTIVE';
```

---

## 7. Test Checklist

### GET /api/categories

- [ ] 200, đủ 4 Root, mỗi Root có `children[]` là Sub-categories ACTIVE
- [ ] Root hoặc Sub-category INACTIVE không xuất hiện
- [ ] Tất cả `createdAt`, `updatedAt` là `null`
- [ ] Sub-category: `children = []`, `hasChildren = false`, `parentId` có giá trị
- [ ] Root: `parentId = null`, `level = 1`

### GET /api/categories/{id}

- [ ] 200, `createdAt` và `updatedAt` có giá trị, `children = null`
- [ ] id không tồn tại → 404 `CAT_001`
- [ ] id của category INACTIVE → 404 `CAT_001`

### POST /api/admin/categories

- [ ] `parentId = null` → Root, `level = 1`, `parentId = null` trong response
- [ ] `parentId = <root id>` → Sub-category, `level = 2`
- [ ] `parentId` trỏ Sub-category → 400 `CAT_006`
- [ ] `parentId` không tồn tại → 404 `CAT_001`
- [ ] `name` trùng cùng parent → 409 `CAT_007`
- [ ] `name` trùng nhưng khác parent → 201
- [ ] `name` bỏ trống → 400 `COMMON_001`
- [ ] `name` vượt 100 ký tự → 400 `COMMON_001`
- [ ] Không có ADMIN role → 403

### PUT /api/admin/categories/{id}

- [ ] Cập nhật thành công, `updatedAt` thay đổi
- [ ] Giữ nguyên `name` cũ → 200
- [ ] `name` mới trùng sibling → 409 `CAT_007`
- [ ] id không tồn tại → 404 `CAT_001`
- [ ] `name` bỏ trống → 400 `COMMON_001`
- [ ] Không có ADMIN role → 403

### DELETE /api/admin/categories/{id}

- [ ] Xóa Sub-category → 204
- [ ] Xóa Root không có Sub-category → 204
- [ ] Xóa Root còn Sub-category → 409 `CAT_002`
- [ ] id không tồn tại → 404 `CAT_001`
- [ ] Không có ADMIN role → 403
