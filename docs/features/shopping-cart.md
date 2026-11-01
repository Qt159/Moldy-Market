# Tính năng Giỏ hàng (Shopping Cart)

## Tổng quan

Giỏ hàng cho phép người dùng:
- Thêm sản phẩm từ các seller khác nhau vào một giỏ hàng chung
- Cập nhật số lượng sản phẩm
- Xóa sản phẩm khỏi giỏ hàng
- Xem tóm tắt giỏ hàng (số lượng & tổng tiền)
- Xem giỏ hàng nhóm theo từng seller (cho phép checkout tuần tự)
- Kiểm tra xem sản phẩm có trong giỏ hàng không

## Kiến trúc

### Thực thể chính

#### Cart (Giỏ hàng)
- **Mục đích**: Lưu thông tin giỏ hàng của người dùng
- **Mối quan hệ**: 1 user ↔ 1 Cart (ACTIVE)
- **Trạng thái**:
  - `ACTIVE`: Giỏ hàng đang sử dụng
  - `ABANDONED`: Bỏ không >30 ngày (tự động đánh dấu)
  - `COMPLETED`: Đã checkout (giỏ hàng xóa)

**Thuộc tính chính**:
```java
- id: UUID (PK)
- user: User (FK)
- status: CartStatus (default: ACTIVE)
- items: List<CartItem> (1:N)
- subtotal: BigDecimal (tính toán từ items)
- totalItems: Integer (tổng quantity)
- createdAt: Instant
- lastUpdatedAt: Instant
- abandonedAt: Instant (khi status = ABANDONED)
```

#### CartItem (Chi tiết sản phẩm trong giỏ hàng)
- **Mục đích**: Lưu chi tiết một sản phẩm trong giỏ hàng
- **Mối quan hệ**: Cart ↔ N CartItem (1:N)
- **Constraint**: 1 listing/cart duy nhất (unique constraint)

**Thuộc tính chính**:
```java
- id: UUID (PK)
- cart: Cart (FK)
- listingId: UUID (không dùng FK để tránh coupling quá chặt)
- sellerId: UUID (dùng để nhóm sản phẩm)
- quantity: Integer (≥1)
- priceAtAddTime: BigDecimal (giá tại thời điểm thêm vào)
- totalPrice: BigDecimal (= quantity * priceAtAddTime)
- note: String (ghi chú của user, tùy chọn)
- createdAt: Instant
- updatedAt: Instant
```

**Lưu ý**: 
- `priceAtAddTime` được lưu cố định, không tự động sync nếu seller thay đổi giá
- User sẽ được thông báo về chênh lệch giá khi checkout

## Workflow chính

### 1. Thêm sản phẩm vào giỏ hàng

```
POST /api/v1/carts/items
{
  "listingId": "550e8400-e29b-41d4-a716-446655440000",
  "quantity": 1,
  "note": "Hỏi trước khi mua" (tùy chọn)
}
```

**Logic**:
1. Lấy giỏ hàng ACTIVE của user (tạo mới nếu chưa có)
2. Kiểm tra sản phẩm đã có trong giỏ chưa:
   - **Nếu có**: Cộng thêm quantity
   - **Nếu chưa**: Tạo CartItem mới
3. Cập nhật tổng tiền và số lượng của Cart
4. Trả về CartResponse (giỏ hàng đầy đủ)

**Response**:
```json
{
  "success": true,
  "message": "Product added to cart successfully",
  "data": {
    "id": "cart-uuid",
    "userId": "user-uuid",
    "status": "ACTIVE",
    "items": [
      {
        "id": "item-uuid",
        "listingId": "listing-uuid",
        "sellerId": "seller-uuid",
        "quantity": 1,
        "priceAtAddTime": 1500000,
        "totalPrice": 1500000,
        "note": null,
        "createdAt": "2026-11-01T10:30:00Z",
        "updatedAt": "2026-11-01T10:30:00Z"
      }
    ],
    "subtotal": 1500000,
    "totalItems": 1,
    "createdAt": "2026-11-01T10:25:00Z",
    "lastUpdatedAt": "2026-11-01T10:30:00Z",
    "abandonedAt": null
  }
}
```

### 2. Cập nhật sản phẩm trong giỏ hàng

```
PATCH /api/v1/carts/items/{listingId}
{
  "quantity": 3,
  "note": "Ghi chú mới" (tùy chọn)
}
```

**Logic**:
1. Lấy giỏ hàng ACTIVE của user
2. Tìm CartItem theo `listingId`
3. Cập nhật `quantity` (phải ≥1)
4. Cập nhật `note` nếu có
5. Cập nhật `totalPrice` = `quantity * priceAtAddTime`
6. Cập nhật tổng tiền của Cart

### 3. Xóa sản phẩm khỏi giỏ hàng

```
DELETE /api/v1/carts/items/{listingId}
```

**Logic**:
1. Lấy giỏ hàng ACTIVE
2. Xóa CartItem theo `listingId`
3. Cập nhật tổng tiền của Cart

### 4. Lấy giỏ hàng đầy đủ

```
GET /api/v1/carts
```

**Response**: Trả về `CartResponse` với tất cả `CartItem` kèm thông tin tổng hợp

### 5. Lấy tóm tắt giỏ hàng

```
GET /api/v1/carts/summary
```

**Dùng cho**: Hiển thị số lượng & tổng tiền trên header cart icon

**Response**:
```json
{
  "success": true,
  "message": "Cart summary retrieved successfully",
  "data": {
    "id": "cart-uuid",
    "status": "ACTIVE",
    "subtotal": 4500000,
    "totalItems": 5
  }
}
```

### 6. Lấy giỏ hàng nhóm theo seller

```
GET /api/v1/carts/by-seller
```

**Dùng cho**: Hiển thị giỏ hàng theo từng gian hàng (cho checkout tuần tự)

**Response**:
```json
{
  "success": true,
  "message": "Cart grouped by seller retrieved successfully",
  "data": [
    {
      "sellerId": "seller-a-uuid",
      "items": [
        {
          "id": "item-uuid",
          "listingId": "listing-uuid",
          "sellerId": "seller-a-uuid",
          "quantity": 2,
          "priceAtAddTime": 1500000,
          "totalPrice": 3000000,
          "note": null,
          "createdAt": "2026-11-01T10:30:00Z",
          "updatedAt": "2026-11-01T10:30:00Z"
        }
      ],
      "subtotalBySeller": 3000000,
      "totalItemsBySeller": 2
    },
    {
      "sellerId": "seller-b-uuid",
      "items": [
        {
          "id": "item-uuid",
          "listingId": "listing-uuid",
          "sellerId": "seller-b-uuid",
          "quantity": 1,
          "priceAtAddTime": 500000,
          "totalPrice": 500000,
          "note": "Ghi chú",
          "createdAt": "2026-11-01T11:00:00Z",
          "updatedAt": "2026-11-01T11:00:00Z"
        }
      ],
      "subtotalBySeller": 500000,
      "totalItemsBySeller": 1
    }
  ]
}
```

### 7. Xóa toàn bộ giỏ hàng

```
DELETE /api/v1/carts
```

**Dùng cho**: 
- User click "Clear cart"
- Sau khi checkout thành công (backend sẽ gọi)

### 8. Kiểm tra sản phẩm có trong giỏ hàng

```
GET /api/v1/carts/items/{listingId}/exists
```

**Dùng cho**: Frontend check để disable/enable nút "Thêm giỏ hàng"

**Response**:
```json
{
  "success": true,
  "message": "Check completed",
  "data": true
}
```

### 9. Đếm số sản phẩm trong giỏ hàng

```
GET /api/v1/carts/items/count
```

**Dùng cho**: Hiển thị số lượng trên cart badge

**Response**:
```json
{
  "success": true,
  "message": "Item count retrieved successfully",
  "data": 5
}
```

## Database Schema

### Bảng `carts`
```sql
CREATE TABLE carts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE', 'ABANDONED', 'COMPLETED')),
    subtotal DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    total_items INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    abandoned_at TIMESTAMP,
    
    CONSTRAINT check_subtotal_positive CHECK (subtotal >= 0),
    CONSTRAINT check_total_items_non_negative CHECK (total_items >= 0)
);

-- Indexes
CREATE INDEX idx_carts_user_id_status ON carts(user_id, status);
CREATE INDEX idx_carts_status_last_updated_at ON carts(status, last_updated_at);
CREATE UNIQUE INDEX idx_carts_user_id_active ON carts(user_id) WHERE status = 'ACTIVE';
```

### Bảng `cart_items`
```sql
CREATE TABLE cart_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL,
    seller_id UUID NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    price_at_add_time DECIMAL(19, 2) NOT NULL,
    total_price DECIMAL(19, 2) NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_quantity_positive CHECK (quantity > 0),
    CONSTRAINT check_price_non_negative CHECK (price_at_add_time >= 0 AND total_price >= 0)
);

-- Indexes
CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX idx_cart_items_listing_id ON cart_items(listing_id);
CREATE INDEX idx_cart_items_seller_id ON cart_items(seller_id);
CREATE UNIQUE INDEX idx_cart_items_cart_id_listing_id ON cart_items(cart_id, listing_id);
```

### Trigger - Auto-update `last_updated_at`
```sql
CREATE OR REPLACE FUNCTION update_cart_last_updated()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE carts SET last_updated_at = CURRENT_TIMESTAMP WHERE id = NEW.cart_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_cart_last_updated_on_item_change
AFTER INSERT OR UPDATE OR DELETE ON cart_items
FOR EACH ROW
EXECUTE FUNCTION update_cart_last_updated();
```

## Bảo mật & Xác thực

- Tất cả endpoints yêu cầu JWT token (Bearer token)
- User chỉ có thể truy cập giỏ hàng của chính họ
- `@AuthenticationPrincipal CustomUserDetails userDetails` được sử dụng để lấy userId

## Lỗi có thể xảy ra

| HTTP Status | Error Message | Nguyên nhân |
|---|---|---|
| 400 | Validation failed | Request body invalid (listingId, quantity, etc.) |
| 401 | Unauthenticated | Không có token hoặc token hết hạn |
| 404 | Cart not found | User chưa có giỏ hàng (rất hiếm) |
| 404 | Item not found in cart | CartItem với listing_id đó không tồn tại |
| 500 | Internal server error | Lỗi database hoặc logic |

## Tính năng nâng cao (Future)

1. **Lưu giỏ hàng tạm thời** (Guest cart)
   - Cho phép user chưa đăng nhập lưu giỏ hàng
   - Merge vào giỏ hàng khi user đăng nhập

2. **Gợi ý sản phẩm liên quan**
   - Khi user xem giỏ hàng, gợi ý sản phẩm tương tự từ các seller khác

3. **Áp dụng Voucher/Discount**
   - Tính tổng tiền sau khi áp dụng voucher
   - Áp dụng giảm giá theo seller hoặc toàn sàn

4. **Batch cleanup**
   - Tự động đánh dấu ABANDONED sau 30 ngày không hoạt động
   - Xóa giỏ hàng ABANDONED sau 60 ngày

5. **Cart persistence** (localStorage + backend sync)
   - Frontend lưu cart vào localStorage
   - Sync với backend khi user đăng nhập

## Testing

### Test scenarios

1. **Thêm sản phẩm**
   - ✓ Thêm sản phẩm mới vào giỏ hàng rỗng
   - ✓ Thêm sản phẩm đã tồn tại (quantity được cộng)
   - ✓ Validation: quantity phải ≥1

2. **Cập nhật sản phẩm**
   - ✓ Cập nhật quantity sản phẩm
   - ✓ Cập nhật note sản phẩm
   - ✓ Validation: quantity phải ≥1

3. **Xóa sản phẩm**
   - ✓ Xóa sản phẩm từ giỏ hàng
   - ✓ Xóa toàn bộ giỏ hàng

4. **Lấy giỏ hàng**
   - ✓ Lấy giỏ hàng đầy đủ
   - ✓ Lấy tóm tắt giỏ hàng
   - ✓ Lấy giỏ hàng nhóm theo seller

5. **Bảo mật**
   - ✓ User không thể truy cập giỏ hàng của user khác
   - ✓ Yêu cầu authentication trước khi truy cập

### Postman Collection

Xem file: `/postman/Shopping-Cart.postman_collection.json`

## Danh sách API Endpoints

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/v1/carts` | Lấy giỏ hàng đầy đủ |
| GET | `/api/v1/carts/summary` | Lấy tóm tắt giỏ hàng |
| GET | `/api/v1/carts/by-seller` | Lấy giỏ hàng nhóm theo seller |
| GET | `/api/v1/carts/items/{listingId}/exists` | Kiểm tra sản phẩm có trong giỏ hàng |
| GET | `/api/v1/carts/items/count` | Đếm số sản phẩm |
| POST | `/api/v1/carts/items` | Thêm sản phẩm vào giỏ hàng |
| PATCH | `/api/v1/carts/items/{listingId}` | Cập nhật sản phẩm |
| DELETE | `/api/v1/carts` | Xóa toàn bộ giỏ hàng |
| DELETE | `/api/v1/carts/items/{listingId}` | Xóa sản phẩm |

