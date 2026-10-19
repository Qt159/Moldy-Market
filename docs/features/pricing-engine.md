# Pricing Engine

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Hai chế độ định giá](#2-hai-chế-độ-định-giá)
3. [Auto Pricing — Điện tử phổ biến](#3-auto-pricing--điện-tử-phổ-biến)
4. [Appraiser Pricing — Định giá qua người định giá](#4-appraiser-pricing--định-giá-qua-người-định-giá)
5. [Database Design](#5-database-design)
6. [API Specification](#6-api-specification)
7. [Business Rules & Validation](#7-business-rules--validation)
8. [Error Cases](#8-error-cases)
9. [Giới hạn đã biết & Hướng phát triển](#9-giới-hạn-đã-biết--hướng-phát-triển)

---

## 1. Tổng quan

Pricing Engine hỗ trợ người bán tham khảo mức giá hợp lý cho sản phẩm đã qua sử dụng dựa trên giá niêm yết tham chiếu và tình trạng thực tế của sản phẩm.

Mục tiêu:
- Cung cấp một mức giá tham khảo (không phải giá ràng buộc) giúp người bán có điểm neo khi định giá sản phẩm.
- Tăng độ tin cậy của listing bằng nhãn "Đã có giá tham khảo".
- Không thay thế quyết định cuối cùng của người bán — người bán luôn là người quyết định giá đăng bán.

Giới hạn phạm vi:
- Giá đề xuất chỉ mang tính tham khảo. Hiển thị kèm nhãn cố định:_"Giá tham khảo — không phải giá thẩm định chính thức"_.
- Hệ thống không xác minh độc lập các thông tin do người bán khai báo trước khi đăng bán.
- Không tích hợp API tra cứu serial/IMEI với nhà sản xuất.
- Giá cuối cùng của listing không bị ép buộc phải bằng giá do Pricing Engine đề xuất.

---

## 2. Hai chế độ định giá

| | Auto Pricing | Appraiser Pricing |
|---|---|---|
| **Áp dụng cho** | Điện tử phổ biến (điện thoại, laptop) — hàng chính hãng VN, có trong bảng tham chiếu | Sản phẩm đồng hồ, đồ thể thao chuyên biệt... hoặc bất kỳ sản phẩm nào người bán muốn có ý kiến chuyên môn |
| **Cách hoạt động** | Hệ thống tự động tính theo công thức và thông tin tình trạng do người bán cung cấp| Người định giá xem ảnh/mô tả thông tin sản phẩm và đưa ra giá đề xuất kèm nhận xét |
| **Nhãn hiển thị** | "Giá tham khảo — không phải giá thẩm định chính thức" | "Đã tham khảo giá bởi [tên người định giá]" + chú thích cố định |
| **Hoa hồng** | Không | Trích từ escrow khi giao dịch thành công |
| **Kết quả** | `SUGGESTED` — giá tham khảo tự động | `APPRAISER_SUGGESTED` — ý kiến chuyên môn cá nhân |

Quy tắc sử dụng: 
- Người bán có thể dùng một trong hai hoặc cả hai.
- Giá đăng bán cuối cùng do người bán tự quyết định.
- Pricing Engine không tự động thay đổi giá đăng bán của listing
- Nếu sử dụng cả hai phương thức, các kết quả được lưu độc lập để người bán có thể so sánh trước khi quyết định giá.
- SUGGESTED và APPRAISER_SUGGESTED là giá đề xuất, không phải giá bắt buộc.

---

## 3. Auto Pricing — Điện tử phổ biến

### 3.1. Điều kiện áp dụng

Auto Pricing chỉ trả về giá đề xuất khi đồng thời thỏa mãn tất cả các điều kiện sau:
- Category là sub-category thuộc **Đồ điện tử**, trong phạm vi hỗ trợ hiện tại gồm: Điện thoại, Laptop.
- Model sản phẩm có trong danh sách model sản phẩm được hỗ trợ Auto Pricing và có giá tham chiếu hiện tại trong  **bảng giá tham chiếu** (`reference_prices`).
- Xuất xứ là **chính hãng Việt Nam** (`origin = OFFICIAL_VN`).

Nếu input hợp lệ nhưng không đáp ứng một hoặc nhiều điều kiện trên, hệ thống không được thực hiện Auto Pricing và trả về MANUAL_REQUIRED. Người bán tự quyết định giá đăng bán và không nhận được giá đề xuất tự động.

Lưu ý: MANUAL_REQUIRED thể hiện sản phẩm không thuộc phạm vi Auto Pricing hoặc không có đủ dữ liệu tham chiếu, không phải lỗi hệ thống.

### 3.2. Input

Người bán tự khai báo các thông tin khi tạo listing:

| Field | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `categoryId` | UUID | Có | Sub-category của sản phẩm, dùng để xác định phạm vi hỗ trợ Auto Pricing |
| `modelId` | UUID | Có (nếu muốn Auto Pricing) | Model sản phẩm thuộc danh sách model được hỗ trợ Auto Pricing |
| `origin` | Enum | Có | `OFFICIAL_VN` |
| `warrantyStatus` | Enum | Có | `UNDER_WARRANTY` / `EXPIRED_UNDER_1Y` / `EXPIRED_OVER_1Y` |
| `conditionLevel` | Enum | Có | `NEW` / `SCRATCHED` / `PARTIAL_DAMAGE` / `HEAVILY_DAMAGED` |
| `usageMonths` | Integer | Có | Số tháng đã sử dụng (≥ 0) |

> **Lưu ý `conditionLevel`:** `PARTIAL_DAMAGE` là mức tổng quát cho các hư hỏng một phần. Người bán mô tả chi tiết trong `description` của listing (Ví dụ: pin chai, vỡ kính, camera hỏng...). Hệ thống không phân biệt từng loại hư hỏng cụ thể trong công thức Auto Pricing; thông tin chi tiết này thuộc về nội dung listing, không phải input định giá.

Lưu ý về modelId: modelId xác định model sản phẩm. Giá tham chiếu hiện tại của model được lấy từ bảng reference_prices trong quá trình tính giá.

### 3.3. Công thức tính

```
Giá đề xuất = Giá tham chiếu * Hệ số khấu hao * Hệ số tình trạng * Hệ số bảo hành
```
Các hệ số được áp dụng độc lập và đồng thời trong quá trình tính giá.

**Giá tham chiếu** (`referencePrice`):
- Là giá thị trường hiện tại của model mới tại thời điểm gần nhất được Admin cập nhật, không phải giá gốc khi sản phẩm ra mắt.
- Ví dụ: iPhone có giá ra mắt 30 triệu đồng, nhưng sau 2 năm giá máy mới trên thị trường còn 20 triệu đồng -> referencePrice = 20.000.000.
- Admin cập nhật thủ công định kỳ 1–3 tháng/lần vào bảng `reference_prices`.
- Khi tính giá, hệ thống sử dụng giá tham chiếu hiện tại của model.

**Hệ số khấu hao theo số tháng sử dụng:**

| Thời gian sử dụng | Hệ số |
|---|---|
| 0–3 tháng | 0.90 |
| 4–6 tháng | 0.82 |
| 7–12 tháng | 0.72 |
| 13–18 tháng | 0.63 |
| 19–24 tháng | 0.55 |
| 25–36 tháng | 0.45 |
| > 36 tháng | 0.35 |

Khấu hao được thiết kế không tuyến tính: mức giảm giá lớn hơn trong giai đoạn đầu và giảm chậm dần khi thời gian sử dụng tăng.

**Hệ số tình trạng (`conditionLevel`):**

| Tình trạng | Hệ số | Mô tả |
|---|---|---|
| `NEW` | 1.00 | Còn mới nguyên, chưa qua sử dụng thực tế |
| `SCRATCHED` | 0.90 | Có vết xước nhỏ, không ảnh hưởng chức năng |
| `PARTIAL_DAMAGE` | 0.70 | Hư hỏng một phần — mô tả chi tiết trong listing |
| `HEAVILY_DAMAGED` | 0.45 | Hỏng nặng, ảnh hưởng nghiêm trọng đến chức năng |

**Hệ số bảo hành (`warrantyStatus`):**

| Trạng thái | Hệ số | Mô tả |
|---|---|---|
| `UNDER_WARRANTY` | 1.05 | Còn bảo hành — tăng nhẹ giá trị |
| `EXPIRED_UNDER_1Y` | 0.97 | Hết bảo hành dưới 1 năm |
| `EXPIRED_OVER_1Y` | 0.90 | Hết bảo hành đã lâu |

> `conditionLevel` và `warrantyStatus` là hai yếu tố **độc lập** trong công thức — không phụ thuộc nhau.

### 3.4. Ví dụ tính

iPhone 15 Pro Max:
- Giá tham chiếu hiện tại: 28,000,000 VND
- Sử dụng 10 tháng → hệ số khấu hao: 0.72
- Tình trạng: `SCRATCHED` → hệ số: 0.90
- Bảo hành: `EXPIRED_UNDER_1Y` → hệ số: 0.97

```
Giá đề xuất = 28,000,000 * 0.72 * 0.90 * 0.97
            = 28,000,000 * 0.62856
            = 17.599.680 VND
            ≈ 17.600.000 VND
```
- Giá đề xuất được làm tròn đến 10.000 VND trước khi trả về kết quả.

### 3.5. Output

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

Khi không đủ điều kiện Auto Pricing:

```json
{
  "pricingMode": "AUTO",
  "status": "MANUAL_REQUIRED",
  "reason": "MODEL_NOT_FOUND",
  "suggestedPrice": null
}
```

`reason` có thể là: `MODEL_NOT_FOUND` / `CATEGORY_NOT_SUPPORTED`.

---

## 4. Appraiser Pricing — Định giá qua người định giá

### 4.1. Thuật ngữ

- Dùng **"người định giá"** (không phải "chuyên gia" hay "trung gian") 

- Người định giá thể hiện đúng phạm vi vai trò: đưa ra ý kiến về mức giá sản phẩm dựa trên thông tin được cung cấp, không xác thực tính chính hãng và không chịu trách nhiệm pháp lý về sản phẩm.

### 4.2. Điều kiện trở thành người định giá

- Đăng ký tài khoản bình thường, sau đó apply làm người định giá cho một hoặc nhiều category.
- Admin duyệt trước khi được hoạt động.
- Admin phải duyệt trước khi người dùng được hoạt động với vai trò người định giá.
- Người định giá chỉ được nhận yêu cầu trong các category đã được Admin duyệt.
- Người định giá không được tự duyệt quyền định giá cho chính mình.

### 4.3. Flow

```
Người bán tạo listing
        │
        ▼
Chọn "Yêu cầu định giá" → chọn category
        │
        ▼
Hệ thống gợi ý người định giá trong category đó
(hoặc người bán chọn thủ công)
        │
        ▼
Gửi yêu cầu → người định giá nhận thông báo
        │
        ▼
Người định giá xem ảnh/mô tả
Có thể request thêm ảnh chi tiết qua chat
        │
        ▼
Người định giá submit: giá đề xuất + nhận xét
        │
        ├── Người bán ĐỒNG Ý
        │         │
        │         ▼
        │   Listing hiển thị nhãn thẩm định
        │   Hoa hồng được ghi nhận, trích khi giải ngân escrow
        │
        └── Người bán TỪ CHỐI
                  │
                  ▼
            Có thể chọn người định giá khác
            (giới hạn: tối đa 3 lần từ chối cho 1 listing)
```
Người bán có thể sử dụng Appraiser Pricing độc lập hoặc kết hợp với Auto Pricing. Nếu sử dụng cả hai, các kết quả được lưu độc lập để người bán tự so sánh và quyết định mức giá đăng bán.

Lưu ý: Pricing Engine không tự động thay đổi giá đăng bán dựa trên kết quả định giá.

### 4.4. Nhãn hiển thị

Khi người bán đồng ý với kết quả định giá:

```
Đã tham khảo giá bởi [Tên người định giá] - [Điểm uy tín]
```
Kèm chú thích cố định:
```
Đây là ý kiến về mức giá, không phải xác nhận về tính chính hãng hoặc chất lượng của sản phẩm.
```

Nếu người định giá chưa đủ 10 giao dịch hoàn chỉnh:

```
Đã tham khảo giá bởi [Tên người định giá]
[Người định giá mới — chưa đủ dữ liệu đánh giá]
```

### 4.5. Điểm uy tín người định giá

Điểm uy tín phản ánh mức độ giá đề xuất của người định giá sát với giá giao dịch thực tế.

**Chỉ tính kết quả vào điểm uy tín khi:**
- Giao dịch hoàn thành thành công (người mua đã xác nhận nhận hàng, không có tranh chấp).
- Giá đề xuất nằm trong khoảng ±20% so với giá bán cuối cùng: Giá bán cuối cùng * 0.8 ≤ Giá đề xuất ≤ Giá bán cuối cùng * 1.2
Kết quả thỏa điều kiện trên được xem là một lần đề xuất sát với giá giao dịch thực tế.

**Hiển thị điểm uy tín:**
- Chỉ hiển thị tỷ lệ % khi người định giá có tối thiểu 10 giao dịch hoàn thành.
- Dưới 10 giao dịch: hiển thị nhãn "Người định giá mới — chưa đủ dữ liệu đánh giá".
- Điểm uy tín được tính: 
    Điểm uy tín = (Số lần đề xuất nằm trong khoảng ±20% *)/Tổng số giao dịch hợp lệ * 100%
- Điểm uy tín chỉ được cập nhật sau khi giao dịch hoàn thành thành công.

### 4.6. Hoa hồng

- Tỷ lệ hoa hồng được thỏa thuận trước giữa người bán và người định giá.
- Tỷ lệ hoa hồng được chốt và ghi nhận tại thời điểm người bán gửi yêu cầu định giá.
- Hoa hồng chỉ được thu khi giao dịch hoàn thành thành công.
- Hoa hồng được trích trực tiếp từ escrow khi giải ngân, không tạo thêm một luồng thanh toán riêng.
  ```
  Người mua thanh toán → Escrow giữ tiền
          │
          ▼ Giải ngân (sau khi confirm nhận hàng)
          │
          ├── Người bán nhận: giá bán − hoa hồng
          └── Người định giá nhận: hoa hồng
  ```
- Nếu listing không phát sinh giao dịch thành công, người định giá không nhận hoa hồng.

### 4.7. Cơ chế kiểm soát đẩy giá

Hệ thống sử dụng cơ chế hậu kiểm thay vì cố gắng tự động xác định người định giá có cố tình đẩy giá hay không.

Người bán hoặc người mua có thể khiếu nại nếu nghi ngờ mức giá đề xuất không hợp lý.

Khiếu nại được xử lý theo nguyên tắc:
- Khiếu nại được Admin xác nhận là hợp lý → trừ điểm uy tín của người định giá.
- Các vi phạm được ghi nhận để Admin theo dõi lịch sử hoạt động.
- Nếu người định giá tích lũy đủ số lần vi phạm theo ngưỡng do Admin quy định -> Admin có thể thu hồi quyền định giá.

-> Hệ thống không cam kết phát hiện hoặc ngăn chặn tuyệt đối hành vi định giá không hợp lý.
---

## 5. Database Design

### Bảng `reference_prices` — Giá tham chiếu model điện tử

```sql
CREATE TABLE reference_prices (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id  UUID         NOT NULL REFERENCES categories(id),
    brand        VARCHAR(100) NOT NULL,
    model_name   VARCHAR(200) NOT NULL,
    reference_price BIGINT   NOT NULL CHECK (reference_price > 0),
    currency     VARCHAR(10)  NOT NULL DEFAULT 'VND',
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by   UUID         REFERENCES users(id),

    CONSTRAINT uq_brand_model UNIQUE (brand, model_name)
);

CREATE INDEX idx_ref_prices_category ON reference_prices(category_id);
CREATE INDEX idx_ref_prices_brand_model ON reference_prices(brand, model_name);
```

### Bảng `pricing_requests` — Yêu cầu định giá

```sql
CREATE TYPE pricing_mode   AS ENUM ('AUTO', 'APPRAISER');
CREATE TYPE pricing_status AS ENUM (
    'SUGGESTED',           -- Auto: đã tính xong; Appraiser: người định giá đã submit
    'MANUAL_REQUIRED',     -- Auto: không đủ điều kiện
    'PENDING',             -- Appraiser: chờ người định giá phản hồi
    'SELLER_REJECTED',     -- Appraiser: người bán từ chối
    'EXPIRED'              -- Appraiser: quá thời hạn phản hồi
);

CREATE TABLE pricing_requests (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id      UUID           NOT NULL REFERENCES listings(id),
    pricing_mode    pricing_mode   NOT NULL,
    status          pricing_status NOT NULL,

    -- Auto pricing input
    model_id            UUID    REFERENCES reference_prices(id),
    origin              VARCHAR(20),  -- OFFICIAL_VN 
    warranty_status     VARCHAR(30),  -- UNDER_WARRANTY / EXPIRED_UNDER_1Y / EXPIRED_OVER_1Y
    condition_level     VARCHAR(20),  -- NEW / SCRATCHED / PARTIAL_DAMAGE / HEAVILY_DAMAGED
    usage_months        INTEGER CHECK (usage_months >= 0),

    -- Auto pricing output
    suggested_price     BIGINT,
    reference_price     BIGINT,
    deprecation_factor  NUMERIC(4,2),
    condition_factor    NUMERIC(4,2),
    warranty_factor     NUMERIC(4,2),

    -- Appraiser pricing
    appraiser_id        UUID  REFERENCES users(id),
    appraiser_note      TEXT,
    commission_rate     NUMERIC(5,2), -- % hoa hồng thỏa thuận
    reject_count        SMALLINT NOT NULL DEFAULT 0,

    -- Disclaimer
    disclaimer          TEXT,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pricing_requests_listing  ON pricing_requests(listing_id);
CREATE INDEX idx_pricing_requests_appraiser ON pricing_requests(appraiser_id);
```

### Bảng `appraiser_profiles` — Hồ sơ người định giá

```sql
CREATE TYPE appraiser_status AS ENUM ('PENDING', 'ACTIVE', 'SUSPENDED');

CREATE TABLE appraiser_profiles (
    id              UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID             NOT NULL UNIQUE REFERENCES users(id),
    status          appraiser_status NOT NULL DEFAULT 'PENDING',
    total_completed INTEGER          NOT NULL DEFAULT 0,
    accurate_count  INTEGER          NOT NULL DEFAULT 0,
    -- accurate_count: số lần đề xuất nằm trong ±20% giá giao dịch thực tế
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE TABLE appraiser_categories (
    appraiser_id UUID REFERENCES appraiser_profiles(id),
    category_id  UUID REFERENCES categories(id),
    PRIMARY KEY (appraiser_id, category_id)
);
```

---

## 6. API Specification

Base URL: `/api`

### Danh sách endpoints

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| `POST` | `/api/pricing/auto` | USER | Tính giá tự động cho listing |
| `POST` | `/api/pricing/appraiser/request` | USER (seller) | Gửi yêu cầu định giá |
| `POST` | `/api/pricing/appraiser/{requestId}/submit` | APPRAISER | Người định giá submit kết quả |
| `POST` | `/api/pricing/appraiser/{requestId}/accept` | USER (seller) | Người bán chấp nhận |
| `POST` | `/api/pricing/appraiser/{requestId}/reject` | USER (seller) | Người bán từ chối |
| `GET` | `/api/pricing/{listingId}` | USER | Xem kết quả định giá của listing |
| `GET` | `/api/admin/reference-prices` | ADMIN | Danh sách giá tham chiếu |
| `POST` | `/api/admin/reference-prices` | ADMIN | Thêm model mới |
| `PUT` | `/api/admin/reference-prices/{id}` | ADMIN | Cập nhật giá tham chiếu |

---

### POST /api/pricing/auto

Tính giá tự động. Gọi trước hoặc trong khi tạo listing.

**Request body**

```json
{
  "categoryId": "uuid-sub-category",
  "modelId": "uuid-reference-price",
  "origin": "OFFICIAL_VN",
  "warrantyStatus": "EXPIRED_UNDER_1Y",
  "conditionLevel": "SCRATCHED",
  "usageMonths": 10
}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "pricingMode": "AUTO",
    "status": "SUGGESTED",
    "suggestedPrice": 17590000,
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

**Response khi không đủ điều kiện (200)**

```json
{
  "success": true,
  "data": {
    "pricingMode": "AUTO",
    "status": "MANUAL_REQUIRED",
    "reason": "MODEL_NOT_FOUND",
    "suggestedPrice": null
  }
}
```

---

### POST /api/pricing/appraiser/request

**Request body**

```json
{
  "listingId": "uuid-listing",
  "appraiserId": "uuid-appraiser",
  "commissionRate": 3.5
}
```

**Response 201**

```json
{
  "success": true,
  "data": {
    "requestId": "uuid-pricing-request",
    "status": "PENDING",
    "appraiserId": "uuid-appraiser",
    "appraiserName": "Nguyen Van B",
    "commissionRate": 3.5,
    "expiredAt": "2026-10-08T17:00:00Z"
  }
}
```

---

### POST /api/pricing/appraiser/{requestId}/submit

Người định giá submit kết quả.

**Request body**

```json
{
  "suggestedPrice": 15000000,
  "note": "Máy còn đẹp, pin khoảng 85%, giá hợp lý với tình trạng thực tế"
}
```

**Response 200** — trả về `pricing_request` đã cập nhật với `status = SUGGESTED`.

---

### PUT /api/admin/reference-prices/{id}

Admin cập nhật giá tham chiếu.

**Request body**

```json
{
  "referencePrice": 27000000
}
```

**Response 200** — trả về record đã cập nhật kèm `updatedAt`.

---

## 7. Business Rules & Validation

| Quy tắc | Mô tả |
|---|---|
| Auto Pricing chỉ cho điện tử chính hãng VN | Category phải là sub của Đồ điện tử, origin = `OFFICIAL_VN`, model có trong `reference_prices` |
| Giá tham chiếu là giá thị trường hiện tại | Không dùng giá ra mắt, Admin cập nhật định kỳ |
| Hệ số là độc lập | `conditionLevel` và `warrantyStatus` nhân riêng biệt, không phụ thuộc nhau |
| `PARTIAL_DAMAGE` là catch-all | Mô tả cụ thể hư hỏng trong listing description, không ảnh hưởng công thức |
| Giới hạn từ chối | Người bán từ chối tối đa 3 lần trên 1 listing |
| Điểm uy tín người định giá | Chỉ hiển thị khi ≥ 10 giao dịch hoàn thành |
| Ngưỡng "sát thực tế" | Giá đề xuất nằm trong ±20% giá giao dịch cuối cùng |
| Hoa hồng chỉ thu khi thành công | Không thu nếu giao dịch thất bại, hủy hoặc tranh chấp chưa giải quyết |
| Nhãn disclaimer bắt buộc | Mọi giá đề xuất (cả Auto lẫn Appraiser) đều hiển thị disclaimer |
| Người bán tự quyết giá cuối | Giá đề xuất không ràng buộc — người bán đặt giá đăng bán độc lập |

---

## 8. Error Cases

| HTTP | Code | Khi nào |
|---|---|---|
| 404 | `PRICING_001` | `modelId` không tồn tại trong `reference_prices` |
| 404 | `PRICING_002` | `listingId` không tồn tại |
| 404 | `PRICING_003` | `appraiserId` không tồn tại hoặc không active |
| 422 | `PRICING_004` | `appraiserId` không hoạt động trong category của listing |
| 422 | `PRICING_005` | Người bán đã từ chối 3 lần — không thể gửi thêm yêu cầu |
| 422 | `PRICING_006` | `usageMonths` < 0 |
| 409 | `PRICING_007` | Listing đã có pricing request đang `PENDING` |
| 403 | `PRICING_008` | Người submit không phải người định giá được assign |

---

## 9. Giới hạn đã biết & Hướng phát triển

### Giới hạn đã biết (chấp nhận trong phạm vi đồ án)

| Giới hạn | Lý do chấp nhận |
|---|---|
| Tình trạng sản phẩm do người bán tự khai, không xác minh trước | Xử lý bằng hậu kiểm — người mua gửi bằng chứng ảnh/video nếu tranh chấp, người bán bị trừ điểm uy tín nếu khai sai |
| `PARTIAL_DAMAGE` gom nhiều loại hư hỏng vào một hệ số | Đánh đổi độ chính xác lấy độ đơn giản của input — phù hợp quy mô đồ án |
| Người bán có thể xin định giá rồi từ chối, tự đăng giá tương tự để né hoa hồng | Tạm thời chưa có cơ chế phù hợp ở quy mô này |
| Không xác minh quyền sở hữu hợp pháp sản phẩm | Thuộc phạm trù pháp lý, ngoài phạm vi bài toán định giá |
| Không xác minh chéo serial/IMEI với hãng | Không được cấp quyền truy cập API chính thức |

### Hướng phát triển (ngoài phạm vi hiện tại)

- Phí định giá cố định hoặc deposit để giảm động cơ "xin giá rồi bỏ".
- Giới hạn tái đăng sau khi đã từ chối người định giá.
- Tách `PARTIAL_DAMAGE` thành các sub-condition cụ thể (pin, màn hình, camera...) kèm hệ số riêng.
- Tự động cập nhật giá tham chiếu từ nguồn dữ liệu thị trường.
