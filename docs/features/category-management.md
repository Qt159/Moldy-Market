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

Category dùng để:

- Phân loại sản phẩm khi đăng bán.
- Filter/search sản phẩm theo danh mục.
- Cho phép Admin quản lý danh mục.
- Hỗ trợ Appraiser và Pricing Engine xác định nhóm sản phẩm.

Hệ thống sử dụng 2 cấp danh mục:

```
Root Category:
    - Sub-category
```

- parentId = null -> Root Category (level = 1)
- parentId = rootId -> Sub-category (level = 2)
- Không hỗ trợ Level 3.

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
| `id` | UUID | Primary key |
| `name` | VARCHAR(100) | Category name |
| `description` | VARCHAR(500) | Optional |
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

Response sử dụng: `ApiResponse<T>` — `{ success, code, message, data, timestamp }`

### Danh sách endpoints

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| `GET` | `/api/categories` | Public | Lấy cây danh mục đang ACTIVE |
| `GET` | `/api/categories/{id}` | Public | Lấy chi tiết một category |
| `POST` | `/api/admin/categories` | ADMIN | Tạo danh mục |
| `PUT` | `/api/admin/categories/{id}` | ADMIN | Cập nhật name / description |
| `DELETE` | `/api/admin/categories/{id}` | ADMIN | Xóa danh mục |

---

### GET /api/categories

  Lấy toàn bộ cây danh mục đang ACTIVE.
    - Chỉ trả về các category có status = ACTIVE.
    - Root Category chứa danh sách children là các Sub-category đang ACTIVE.
    - Sub-category có children = [].
    - Dữ liệu được lấy bằng một DB query và build thành cây trong memory.
    - createdAt và updatedAt không được sử dụng trong response dạng cây nên trả về null

**Response 200**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Request completed successfully",
  "data": [
     {
      "id": "uuid",
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
          "parentId": "uuid",
          "level": 2,
          "status": "ACTIVE",
          "hasChildren": false,
          "children": []
        } 
      ]
   } 
  ]
}
```

---

### GET /api/categories/{id}

Lấy thông tin chi tiết của một category đang ACTIVE.
 - children = null.
 - createdAt và updatedAt được trả về.

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
    "parentId": "uuid-root",
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
| 404 | `CAT_001` | Category không tồn tại hoặc đang INACTIVE |

---

### POST /api/admin/categories

Tạo Root Category hoặc Sub-category.
 - parentId = null -> tạo Root Category.
 - parentId có giá trị -> tạo Sub-category.
 - parentId phải trỏ đến Root Category.
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

Cập nhật thông tin category.
Chỉ được cập nhật: name, description.
Không được thay đổi: parentId, level.
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
| 404 | `CAT_001` | Category không tồn tại |
| 409 | `CAT_007` | `name` mới trùng với category cùng parent|

---

### DELETE /api/admin/categories/{id}

Xóa category khỏi hệ thống.

Quy tắc:

- Root Category không được xóa nếu vẫn còn Sub-category.
- Sub-category hiện tại có thể xóa. 
- Kiểm tra Product/Listing sẽ được bổ sung khi Product module hoàn thiện.

**Response 204** — No Content.

**Lỗi**

| HTTP | Code | Khi nào |
|---|---|---|
| 404 | `CAT_001` | Category không tồn tại |
| 409 | `CAT_002` | Root Category vẫn còn Sub-category |

---

## 5. Business Rules & Validation

| Quy tắc | Mô tả |
|---|---|
| Cấu trúc          | Chỉ hỗ trợ 2 cấp: Root → Sub-category |
| Parent            | Sub-category phải có Root Category làm parent |
| Tên danh mục      | Không được trùng trong cùng một parent |
| Cập nhật          | Không được thay đổi parentId và level |
| Xóa Root          | Không được xóa khi vẫn còn Sub-category |
| Xóa Sub-category  | Hiện tại cho phép xóa|
| Quyền Admin       | Các API /api/admin/** yêu cầu role ADMIN |
| Category sản phẩm | Sản phẩm chỉ được sử dụng Sub-category đang ACTIVE |

### Kiểm tra Category khi đăng bán sản phẩm

Khi tạo hoặc cập nhật sản phẩm, Product module gọi: categoryService.validateProductCategory(categoryId);
Thứ tự kiểm tra:
1: Category tồn tại nếu lỗi thì 404 CAT_001
2: Category đang ACTIVE nếu lỗi thì 422 CAT_004
3: Category là Sub-category nếu lỗi thì 422 CAT_005

---
## 6. Product Integration

Product tham chiếu đến Category thông qua category_id:

ALTER TABLE products
    ADD COLUMN category_id UUID NOT NULL
    REFERENCES categories(id);

Khi tạo hoặc cập nhật Product, categoryId phải là một Sub-category đang ACTIVE.

Product module gọi validation từ CategoryService: categoryServicevalidateProductCategory(categoryId);
Tìm sản phẩm theo Root Category

Do Category chỉ có 2 cấp nên không cần recursive query:

SELECT p.*
FROM products p
JOIN categories c ON p.category_id = c.id
WHERE (c.id = :rootId OR c.parent_id = :rootId)
  AND c.status = 'ACTIVE';

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
