# Moldy Market — Database Design Document
---

## 1. Tổng quan

Moldy Market sử dụng kiến trúc lưu trữ dữ liệu kết hợp giữa **PostgreSQL**, **DynamoDB** và **Amazon S3**.

### Mục tiêu

- Đảm bảo tính toàn vẹn cho dữ liệu nghiệp vụ.
- Hỗ trợ transaction cho các nghiệp vụ quan trọng như Order và Payment.
- Tách biệt workload của Notification khỏi dữ liệu nghiệp vụ chính.
- Lưu trữ hình ảnh hiệu quả mà không làm tăng kích thước database.

---

## 2. Kiến trúc Database

### 2.1 PostgreSQL — Dữ liệu nghiệp vụ

| # | Bảng | Mô tả |
|---|---|---|
| 1 | `users` | Tài khoản người dùng |
| 2 | `stores` | Gian hàng |
| 3 | `store_staff` | Nhân viên gian hàng và phân quyền |
| 4 | `appraiser_profiles` | Hồ sơ và trạng thái Appraiser |
| 5 | `categories` | Danh mục sản phẩm |
| 6 | `listings` | Sản phẩm đăng bán |
| 7 | `favorites` | Sản phẩm yêu thích |
| 8 | `appraisal_requests` | Yêu cầu thẩm định |
| 9 | `price_predictions` | Kết quả định giá tự động |
| 10 | `offers` | Đề nghị giá |
| 11 | `orders` | Đơn hàng |
| 12 | `payments` | Thanh toán và Escrow |
| 13 | `shipments` | Vận chuyển |
| 14 | `disputes` | Tranh chấp và khiếu nại |
| 15 | `reviews` | Đánh giá |
| 16 | `vouchers` | Voucher |
| 17 | `voucher_usages` | Lịch sử sử dụng voucher |
| 18 | `wallet_transactions` | Lịch sử biến động ví |
| 19 | `chat_rooms` | Phòng chat |
| 20 | `chat_messages` | Tin nhắn |

### 2.2 DynamoDB — Notifications

Notification được tách khỏi PostgreSQL để tránh workload thông báo ảnh hưởng đến các nghiệp vụ quan trọng.

### 2.3 Amazon S3 — File Storage

Lưu ảnh sản phẩm, avatar, bằng chứng tranh chấp, và hồ sơ Appraiser.

---

# 3. PostgreSQL Schema

## 3.1 `users`

Lưu thông tin tài khoản người dùng.

| Column          | Type        | Constraint       | Mô tả |
|---|---|---|---|
| `id`            | UUID        | PK               | Mã người dùng |
| `email`         | VARCHAR     | UNIQUE, NOT NULL | Email đăng nhập |
| `password_hash` | VARCHAR     | NULL             | Mật khẩu đã hash (NULL nếu đăng nhập OAuth) |
| `full_name`     | VARCHAR     | NOT NULL         | Họ và tên |
| `phone`         | VARCHAR     | UNIQUE, NULL     | Số điện thoại |
| `avatar_url`    | VARCHAR     | NULL             | Object key ảnh đại diện trên S3 |
| `role`          | VARCHAR     | NOT NULL         | `USER`, `ADMIN` |
| `is_appraiser`  | BOOLEAN     | DEFAULT FALSE    | Có quyền thẩm định không |
| `is_store_owner`| BOOLEAN     | DEFAULT FALSE    | Có gian hàng không |
| `legit_points`  | INTEGER     | DEFAULT 100      | Điểm tín nhiệm |
| `bank_account`  | TEXT        | NULL             | Thông tin ngân hàng (mã hóa AES-256) |
| `status`        | VARCHAR     | NOT NULL         | `ACTIVE`, `BLOCKED` |
| `created_at`    | TIMESTAMP   | NOT NULL         | Thời điểm tạo |
| `updated_at`    | TIMESTAMP   | NOT NULL         | Thời điểm cập nhật |

**Ghi chú:**
- `role` chỉ phân biệt `USER` và `ADMIN`. Store Owner và Appraiser là trạng thái bổ sung, không phải role riêng.
- `bank_account` lưu dạng JSON đã mã hóa: `{ bankName, accountNumber, accountName }`.

---

## 3.2 `stores`

Lưu thông tin gian hàng của Store Owner.

| Column           | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`             | UUID      | PK                  | Mã gian hàng |
| `owner_id`       | UUID      | FK → `users.id`, UNIQUE | Chủ gian hàng (1 user chỉ có 1 shop) |
| `name`           | VARCHAR   | UNIQUE, NOT NULL    | Tên gian hàng |
| `description`    | TEXT      | NULL                | Mô tả |
| `logo_url`       | VARCHAR   | NULL                | Object key logo trên S3 |
| `pickup_address` | TEXT      | NULL                | Địa chỉ lấy hàng |
| `status`         | VARCHAR   | NOT NULL            | `ACTIVE`, `BLOCKED` |
| `created_at`     | TIMESTAMP | NOT NULL            | Thời điểm tạo |
| `updated_at`     | TIMESTAMP | NOT NULL            | Thời điểm cập nhật |

---

## 3.3 `store_staff`

Lưu mối quan hệ giữa gian hàng và nhân viên, kèm danh sách quyền.

| Column       | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`         | UUID      | PK                  | Mã bản ghi |
| `store_id`   | UUID      | FK → `stores.id`    | Gian hàng |
| `user_id`    | UUID      | FK → `users.id`     | Tài khoản nhân viên |
| `permissions`| JSONB     | NOT NULL            | Danh sách quyền được gán |
| `created_at` | TIMESTAMP | NOT NULL            | Thời điểm thêm |

**Unique Constraint:** `(store_id, user_id)`

**Ví dụ `permissions`:**
```json
["MANAGE_PRODUCTS", "HANDLE_ORDERS", "CHAT_WITH_CUSTOMERS"]
```

---

## 3.4 `appraiser_profiles`

Lưu hồ sơ đăng ký và trạng thái của Appraiser.

| Column          | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`            | UUID      | PK                  | Mã hồ sơ |
| `user_id`       | UUID      | FK → `users.id`, UNIQUE | Người đăng ký |
| `specialties`   | JSONB     | NOT NULL            | Danh sách chuyên môn |
| `doc_keys`      | JSONB     | NULL                | Object key tài liệu/chứng chỉ trên S3 |
| `status`        | VARCHAR   | NOT NULL            | `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED` |
| `reviewed_by`   | UUID      | FK → `users.id`, NULL | Admin đã duyệt |
| `reviewed_at`   | TIMESTAMP | NULL                | Thời điểm duyệt |
| `reject_reason` | TEXT      | NULL                | Lý do từ chối |
| `created_at`    | TIMESTAMP | NOT NULL            | Thời điểm nộp |

**Ví dụ `specialties`:** `["WATCHES", "FURNITURE", "JEWELRY"]`

---

## 3.5 `categories`

Lưu danh mục sản phẩm do Admin quản lý.

| Column               | Type      | Constraint       | Mô tả |
|---|---|---|---|
| `id`                 | UUID      | PK               | Mã danh mục |
| `name`               | VARCHAR   | UNIQUE, NOT NULL | Tên danh mục |
| `description`        | TEXT      | NULL             | Mô tả |
| `requires_appraisal` | BOOLEAN   | DEFAULT FALSE    | Cần thẩm định hay không |
| `is_electronic`      | BOOLEAN   | DEFAULT FALSE    | Là thiết bị điện tử (dùng Auto-Pricing) |
| `status`             | VARCHAR   | NOT NULL         | `ACTIVE`, `INACTIVE` |
| `created_at`         | TIMESTAMP | NOT NULL         | Thời điểm tạo |

---

## 3.6 `listings`

Lưu thông tin sản phẩm đăng bán.

| Column            | Type      | Constraint              | Mô tả |
|---|---|---|---|
| `id`              | UUID      | PK                      | Mã sản phẩm |
| `seller_id`       | UUID      | FK → `users.id`         | Người bán |
| `store_id`        | UUID      | FK → `stores.id`, NULL  | Gian hàng (NULL nếu bán cá nhân) |
| `category_id`     | UUID      | FK → `categories.id`    | Danh mục |
| `title`           | VARCHAR   | NOT NULL                | Tiêu đề |
| `description`     | TEXT      | NULL                    | Mô tả |
| `price`           | DECIMAL   | NOT NULL                | Giá bán hiện tại |
| `original_price`  | DECIMAL   | NULL                    | Giá gốc khi mua mới |
| `condition`       | VARCHAR   | NOT NULL                | `NEW`, `LIKE_NEW`, `GOOD`, `FAIR`, `POOR` |
| `usage_duration`  | INTEGER   | NULL                    | Số tháng đã dùng |
| `warranty_status` | VARCHAR   | NULL                    | `IN_WARRANTY`, `EXPIRED`, `NO_WARRANTY` |
| `allow_offer`     | BOOLEAN   | DEFAULT TRUE            | Cho phép trả giá |
| `min_offer_price` | DECIMAL   | NULL                    | Mức giá sàn cho offer (nếu có) |
| `location`        | VARCHAR   | NULL                    | Khu vực |
| `image_keys`      | JSONB     | NULL                    | Danh sách object key ảnh trên S3 |
| `status`          | VARCHAR   | NOT NULL                | `ACTIVE`, `SOLD`, `HIDDEN`, `EXPIRED` |
| `created_at`      | TIMESTAMP | NOT NULL                | Thời điểm đăng |
| `updated_at`      | TIMESTAMP | NOT NULL                | Thời điểm cập nhật |
| `sold_at`         | TIMESTAMP | NULL                    | Thời điểm bán |
| `deleted_at`      | TIMESTAMP | NULL                    | Soft delete |

**Ghi chú:** `deleted_at IS NOT NULL` → listing bị xóa mềm, không hiện trên search nhưng vẫn liên kết được với order/offer cũ.

---

## 3.7 `favorites`

Lưu danh sách sản phẩm yêu thích.

| Column       | Type      | Constraint              | Mô tả |
|---|---|---|---|
| `user_id`    | UUID      | PK, FK → `users.id`     | Người dùng |
| `listing_id` | UUID      | PK, FK → `listings.id`  | Sản phẩm |
| `created_at` | TIMESTAMP | NOT NULL                | Thời điểm thêm |

**Primary Key:** `(user_id, listing_id)`

---

## 3.8 `appraisal_requests`

Lưu yêu cầu thẩm định từ người bán cho sản phẩm phi điện tử.

| Column           | Type      | Constraint                       | Mô tả |
|---|---|---|---|
| `id`             | UUID      | PK                               | Mã yêu cầu |
| `listing_id`     | UUID      | FK → `listings.id`               | Sản phẩm cần thẩm định |
| `seller_id`      | UUID      | FK → `users.id`                  | Người bán |
| `appraiser_id`   | UUID      | FK → `users.id`, NULL            | Appraiser đang xử lý |
| `suggested_price`| DECIMAL   | NULL                             | Giá đề xuất sau thẩm định |
| `appraiser_note` | TEXT      | NULL                             | Nhận xét của Appraiser |
| `status`         | VARCHAR   | NOT NULL                         | `WAITING`, `IN_PROGRESS`, `COMPLETED`, `TIMEOUT` |
| `fee`            | DECIMAL   | NOT NULL                         | Phí dịch vụ thẩm định |
| `assigned_at`    | TIMESTAMP | NULL                             | Thời điểm Appraiser nhận |
| `deadline_at`    | TIMESTAMP | NULL                             | Thời hạn phải hoàn thành (48h) |
| `completed_at`   | TIMESTAMP | NULL                             | Thời điểm hoàn thành |
| `created_at`     | TIMESTAMP | NOT NULL                         | Thời điểm tạo |

---

## 3.9 `price_predictions`

Lưu kết quả định giá tự động của Auto-Pricing Engine.

| Column             | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`               | UUID      | PK                  | Mã prediction |
| `listing_id`       | UUID      | FK → `listings.id`  | Sản phẩm |
| `estimated_price`  | DECIMAL   | NOT NULL            | Giá đề xuất |
| `min_price`        | DECIMAL   | NULL                | Mức giá thấp |
| `max_price`        | DECIMAL   | NULL                | Mức giá cao |
| `confidence_score` | DECIMAL   | NULL                | Độ tin cậy (0.0 – 1.0) |
| `created_at`       | TIMESTAMP | NOT NULL            | Thời điểm tính |

---

## 3.10 `offers`

Lưu đề nghị giá giữa buyer và seller.

| Column          | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`            | UUID      | PK                  | Mã offer |
| `listing_id`    | UUID      | FK → `listings.id`  | Sản phẩm |
| `buyer_id`      | UUID      | FK → `users.id`     | Người mua |
| `seller_id`     | UUID      | FK → `users.id`     | Người bán |
| `offered_price` | DECIMAL   | NOT NULL            | Giá đề nghị |
| `status`        | VARCHAR   | NOT NULL            | `PENDING`, `ACCEPTED`, `REJECTED`, `COUNTERED`, `EXPIRED`, `CANCELLED` |
| `expires_at`    | TIMESTAMP | NOT NULL            | Thời điểm hết hạn |
| `created_at`    | TIMESTAMP | NOT NULL            | Thời điểm tạo |
| `updated_at`    | TIMESTAMP | NOT NULL            | Thời điểm cập nhật |

**Business Rule:** `buyer_id != seller_id`

---

## 3.11 `orders`

Lưu thông tin đơn hàng.

| Column             | Type      | Constraint                  | Mô tả |
|---|---|---|---|
| `id`               | UUID      | PK                          | Mã đơn hàng |
| `listing_id`       | UUID      | FK → `listings.id`          | Sản phẩm |
| `buyer_id`         | UUID      | FK → `users.id`             | Người mua |
| `seller_id`        | UUID      | FK → `users.id`             | Người bán |
| `offer_id`         | UUID      | FK → `offers.id`, NULL      | Offer được dùng (nếu có) |
| `voucher_id`       | UUID      | FK → `vouchers.id`, NULL    | Voucher áp dụng (nếu có) |
| `amount`           | DECIMAL   | NOT NULL                    | Giá sản phẩm |
| `voucher_discount` | DECIMAL   | DEFAULT 0                   | Số tiền giảm từ voucher |
| `shipping_fee`     | DECIMAL   | DEFAULT 0                   | Phí vận chuyển |
| `total_amount`     | DECIMAL   | NOT NULL                    | Tổng tiền phải thanh toán |
| `status`           | VARCHAR   | NOT NULL                    | `PENDING_PAYMENT`, `PAID_AWAITING_PREPARATION`, `PREPARING`, `SHIPPED`, `DELIVERED`, `COMPLETED`, `CANCELLED`, `DISPUTED` |
| `delivery_method`  | VARCHAR   | NOT NULL                    | `SHIPPING`, `MEETUP` |
| `delivery_address` | TEXT      | NULL                        | Địa chỉ giao hàng |
| `cancel_reason`    | TEXT      | NULL                        | Lý do hủy |
| `cancelled_by`     | UUID      | FK → `users.id`, NULL       | Ai hủy |
| `created_at`       | TIMESTAMP | NOT NULL                    | Thời điểm tạo |
| `updated_at`       | TIMESTAMP | NOT NULL                    | Thời điểm cập nhật |
| `completed_at`     | TIMESTAMP | NULL                        | Thời điểm hoàn tất |

---

## 3.12 `payments`

Lưu thông tin thanh toán và trạng thái Escrow. Tích hợp với **SePay** qua cơ chế bank transfer webhook.

| Column           | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`             | UUID      | PK                  | Mã thanh toán |
| `order_id`       | UUID      | FK → `orders.id`    | Đơn hàng |
| `transfer_code`  | VARCHAR   | UNIQUE, NOT NULL    | Mã nội dung chuyển khoản unique (VD: `MM-ORDER-abc123`) |
| `bank_txn_id`    | VARCHAR   | UNIQUE, NULL        | Mã giao dịch ngân hàng từ SePay webhook (dùng cho idempotency) |
| `amount`         | DECIMAL   | NOT NULL            | Số tiền cần thanh toán |
| `status`         | VARCHAR   | NOT NULL            | `PENDING`, `SUCCESS`, `FAILED`, `EXPIRED` |
| `escrow_status`  | VARCHAR   | NOT NULL            | `HELD`, `RELEASED`, `REFUNDED`, `DISPUTED`, `FROZEN` |
| `paid_at`        | TIMESTAMP | NULL                | Thời điểm SePay xác nhận nhận tiền |
| `expires_at`     | TIMESTAMP | NOT NULL            | Thời hạn chờ thanh toán (sau đó tự hủy đơn) |
| `released_at`    | TIMESTAMP | NULL                | Thời điểm giải ngân cho seller |
| `refunded_at`    | TIMESTAMP | NULL                | Thời điểm hoàn tiền cho buyer |
| `created_at`     | TIMESTAMP | NOT NULL            | Thời điểm tạo |
| `updated_at`     | TIMESTAMP | NOT NULL            | Thời điểm cập nhật |

**Ghi chú SePay flow:**
- Khi tạo order → tạo payment với `status=PENDING`, sinh `transfer_code`.
- SePay gọi webhook → backend verify `transfer_code` + `amount` → cập nhật `status=SUCCESS`, lưu `bank_txn_id`.
- `bank_txn_id` dùng làm idempotency key — webhook trùng sẽ bị bỏ qua.

---

## 3.13 `shipments`

Lưu thông tin vận chuyển.

| Column             | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`               | UUID      | PK                  | Mã vận chuyển |
| `order_id`         | UUID      | FK → `orders.id`, UNIQUE | Đơn hàng (1 order chỉ có 1 shipment) |
| `carrier`          | VARCHAR   | NOT NULL            | Đơn vị vận chuyển (`GHN`, `GHTK`) |
| `tracking_number`  | VARCHAR   | UNIQUE, NULL        | Mã vận đơn |
| `shipping_fee`     | DECIMAL   | NOT NULL            | Phí vận chuyển |
| `status`           | VARCHAR   | NOT NULL            | `PENDING`, `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `FAILED` |
| `pickup_address`   | TEXT      | NOT NULL            | Địa chỉ lấy hàng |
| `delivery_address` | TEXT      | NOT NULL            | Địa chỉ giao hàng |
| `shipped_at`       | TIMESTAMP | NULL                | Thời điểm bàn giao cho bưu tá |
| `delivered_at`     | TIMESTAMP | NULL                | Thời điểm giao thành công |
| `created_at`       | TIMESTAMP | NOT NULL            | Thời điểm tạo |
| `updated_at`       | TIMESTAMP | NOT NULL            | Thời điểm cập nhật |

---

## 3.14 `disputes`

Lưu thông tin tranh chấp và khiếu nại của đơn hàng.

| Column           | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`             | UUID      | PK                  | Mã tranh chấp |
| `order_id`       | UUID      | FK → `orders.id`    | Đơn hàng liên quan |
| `opened_by`      | UUID      | FK → `users.id`     | Người mở tranh chấp |
| `reason`         | VARCHAR   | NOT NULL            | Lý do (`ITEM_NOT_AS_DESCRIBED`, `ITEM_NOT_RECEIVED`, `DAMAGED`, `FAKE_ITEM`, `OTHER`) |
| `description`    | TEXT      | NULL                | Mô tả chi tiết |
| `evidence_keys`  | JSONB     | NULL                | Object key ảnh/video bằng chứng trên S3 |
| `status`         | VARCHAR   | NOT NULL            | `OPEN`, `UNDER_REVIEW`, `RESOLVED_REFUND`, `RESOLVED_RELEASE`, `CLOSED` |
| `admin_id`       | UUID      | FK → `users.id`, NULL | Admin xử lý |
| `admin_note`     | TEXT      | NULL                | Ghi chú quyết định của Admin |
| `resolved_at`    | TIMESTAMP | NULL                | Thời điểm giải quyết |
| `created_at`     | TIMESTAMP | NOT NULL            | Thời điểm tạo |
| `updated_at`     | TIMESTAMP | NOT NULL            | Thời điểm cập nhật |

---

## 3.15 `reviews`

Lưu đánh giá sau giao dịch.

| Column        | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`          | UUID      | PK                  | Mã đánh giá |
| `order_id`    | UUID      | FK → `orders.id`    | Đơn hàng |
| `reviewer_id` | UUID      | FK → `users.id`     | Người đánh giá |
| `reviewee_id` | UUID      | FK → `users.id`     | Người được đánh giá |
| `rating`      | INTEGER   | NOT NULL            | Điểm (1–5) |
| `comment`     | TEXT      | NULL                | Nội dung đánh giá |
| `reply`       | TEXT      | NULL                | Phản hồi của người được đánh giá |
| `reply_at`    | TIMESTAMP | NULL                | Thời điểm phản hồi |
| `created_at`  | TIMESTAMP | NOT NULL            | Thời điểm tạo |
| `updated_at`  | TIMESTAMP | NOT NULL            | Thời điểm cập nhật |

**Unique Constraint:** `(order_id, reviewer_id)`

---

## 3.16 `vouchers`

Lưu thông tin voucher.

| Column            | Type      | Constraint              | Mô tả |
|---|---|---|---|
| `id`              | UUID      | PK                      | Mã voucher |
| `seller_id`       | UUID      | FK → `users.id`, NULL   | Người tạo (`NULL` = voucher toàn sàn của Admin) |
| `store_id`        | UUID      | FK → `stores.id`, NULL  | Gian hàng áp dụng (`NULL` = toàn sàn hoặc cá nhân) |
| `code`            | VARCHAR   | UNIQUE, NOT NULL        | Mã code |
| `discount_type`   | VARCHAR   | NOT NULL                | `PERCENT`, `FIXED` |
| `discount_value`  | DECIMAL   | NOT NULL                | Giá trị giảm |
| `max_discount`    | DECIMAL   | NULL                    | Mức giảm tối đa (dùng cho PERCENT) |
| `min_order_value` | DECIMAL   | NULL                    | Giá trị đơn tối thiểu |
| `usage_limit`     | INTEGER   | NULL                    | Số lần sử dụng tối đa |
| `used_count`      | INTEGER   | DEFAULT 0               | Số lần đã dùng |
| `start_at`        | TIMESTAMP | NOT NULL                | Thời điểm bắt đầu |
| `expires_at`      | TIMESTAMP | NOT NULL                | Thời điểm hết hạn |
| `status`          | VARCHAR   | NOT NULL                | `ACTIVE`, `INACTIVE`, `EXPIRED` |
| `created_at`      | TIMESTAMP | NOT NULL                | Thời điểm tạo |

---

## 3.17 `voucher_usages`

Lưu lịch sử sử dụng voucher.

| Column            | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`              | UUID      | PK                  | Mã bản ghi |
| `voucher_id`      | UUID      | FK → `vouchers.id`  | Voucher |
| `user_id`         | UUID      | FK → `users.id`     | Người dùng |
| `order_id`        | UUID      | FK → `orders.id`    | Đơn hàng |
| `discount_amount` | DECIMAL   | NOT NULL            | Số tiền thực tế được giảm |
| `used_at`         | TIMESTAMP | NOT NULL            | Thời điểm sử dụng |

**Unique Constraint:** `(voucher_id, user_id)` — mỗi user chỉ dùng một voucher một lần.

---

## 3.18 `wallet_transactions`

Lưu toàn bộ lịch sử biến động số dư ví của mọi tài khoản (user, appraiser, store).

| Column           | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`             | UUID      | PK                  | Mã giao dịch ví |
| `user_id`        | UUID      | FK → `users.id`     | Chủ ví |
| `order_id`       | UUID      | FK → `orders.id`, NULL | Đơn hàng liên quan |
| `type`           | VARCHAR   | NOT NULL            | `ESCROW_IN`, `ESCROW_RELEASE`, `ESCROW_REFUND`, `APPRAISAL_FEE`, `WITHDRAWAL` |
| `amount`         | DECIMAL   | NOT NULL            | Số tiền (dương = vào, âm = ra) |
| `balance_after`  | DECIMAL   | NOT NULL            | Số dư sau giao dịch |
| `note`           | TEXT      | NULL                | Ghi chú |
| `created_at`     | TIMESTAMP | NOT NULL            | Thời điểm giao dịch |

---

## 3.19 `chat_rooms`

Lưu phòng chat giữa hai người dùng.

| Column       | Type      | Constraint          | Mô tả |
|---|---|---|---|
| `id`         | UUID      | PK                  | Mã phòng chat |
| `user_a_id`  | UUID      | FK → `users.id`     | Người dùng A (id nhỏ hơn) |
| `user_b_id`  | UUID      | FK → `users.id`     | Người dùng B (id lớn hơn) |
| `listing_id` | UUID      | FK → `listings.id`, NULL | Sản phẩm liên quan |
| `created_at` | TIMESTAMP | NOT NULL            | Thời điểm tạo |

**Unique Constraint:** `(user_a_id, user_b_id)` — một cặp user chỉ có một phòng chat.

**Quy ước:** `user_a_id < user_b_id` (sort UUID để đảm bảo tính nhất quán khi tạo room).

---

## 3.20 `chat_messages`

Lưu tin nhắn trong phòng chat.

| Column        | Type      | Constraint               | Mô tả |
|---|---|---|---|
| `id`          | UUID      | PK                       | Mã tin nhắn |
| `room_id`     | UUID      | FK → `chat_rooms.id`     | Phòng chat |
| `sender_id`   | UUID      | FK → `users.id`          | Người gửi |
| `content`     | TEXT      | NULL                     | Nội dung tin nhắn |
| `image_key`   | VARCHAR   | NULL                     | Object key ảnh đính kèm trên S3 |
| `type`        | VARCHAR   | NOT NULL                 | `TEXT`, `IMAGE`, `LISTING_LINK` |
| `created_at`  | TIMESTAMP | NOT NULL                 | Thời điểm gửi |

---

# 4. DynamoDB Schema

## 4.1 `notifications`

Lưu notification của người dùng. Tách khỏi PostgreSQL để cô lập workload và TTL tự động.

### Primary Key

```
PK = USER#{userId}
SK = NOTIFICATION#{timestamp}#{notificationId}
```

**Ví dụ:**
```
PK: USER#550e8400-e29b-41d4-a716-446655440000
SK: NOTIFICATION#2026-09-17T10:30:00Z#abc-123
```

### Attributes

| Attribute        | Mô tả |
|---|---|
| `notificationId` | Mã notification |
| `type`           | Loại: `OFFER_RECEIVED`, `ORDER_STATUS_CHANGED`, `PAYMENT_RECEIVED`, `ESCROW_RELEASED`, `DISPUTE_OPENED`, ... |
| `title`          | Tiêu đề |
| `content`        | Nội dung |
| `referenceType`  | `ORDER`, `OFFER`, `DISPUTE`, ... |
| `referenceId`    | Mã đối tượng tham chiếu |
| `isRead`         | `true` / `false` |
| `createdAt`      | Thời điểm tạo (ISO 8601) |
| `expiresAt`      | TTL — DynamoDB tự xóa sau thời điểm này |

### Access Patterns

| Pattern | Query |
|---|---|
| Lấy tất cả notification của user | `PK = USER#{userId}` |
| Lấy notification chưa đọc | `PK = USER#{userId}` + filter `isRead = false` |
| Lấy một notification cụ thể | `PK = USER#{userId}`, `SK = NOTIFICATION#...` |
| Đánh dấu đã đọc | Update `isRead = true` |

---

# 5. Amazon S3 Storage

## Bucket Structure

```
S3 Bucket: moldy-market-storage
│
├── listings/
│   └── {listingId}/
│       ├── {uuid}.jpg
│       └── {uuid}.jpg
│
├── users/
│   └── {userId}/
│       └── avatar/
│           └── {uuid}.jpg
│
├── disputes/
│   └── {disputeId}/
│       ├── {uuid}.jpg
│       └── {uuid}.mp4
│
├── appraisers/
│   └── {userId}/
│       ├── certificate-{uuid}.pdf
│       └── id-card-{uuid}.jpg
│
└── stores/
    └── {storeId}/
        └── logo-{uuid}.jpg
```

## Access Control

| Path | Quyền truy cập |
|---|---|
| `listings/**` | Public read (qua CloudFront CDN) |
| `users/*/avatar/**` | Public read |
| `disputes/**` | Private — chỉ backend và Admin (Presigned URL, TTL 1h) |
| `appraisers/**` | Private — chỉ backend và Admin |
| `stores/*/logo/**` | Public read |

---

# 6. Database Relationships

## Sơ đồ quan hệ chính

```
users ──────────────────────┐
  │                          │
  ├── stores (1:1)            │
  │     └── store_staff (1:N) │
  │                          │
  ├── appraiser_profiles (1:1)│
  │                          │
  ├── listings (1:N) ─────────┤
  │     ├── favorites (N:M)   │
  │     ├── appraisal_requests│
  │     ├── price_predictions │
  │     └── offers (1:N) ─────┤
  │           └── orders ──── │
  │                 ├── payments
  │                 ├── shipments
  │                 ├── disputes
  │                 ├── reviews
  │                 └── voucher_usages
  │
  ├── chat_rooms (N:M via user_a/user_b)
  │     └── chat_messages (1:N)
  │
  └── wallet_transactions (1:N)
```

## Bảng quan hệ đầy đủ

| Quan hệ | Cardinality | Ghi chú |
|---|---|---|
| `users → stores` | 1:1 | Mỗi user chỉ có tối đa 1 shop |
| `stores → store_staff` | 1:N | Shop có nhiều nhân viên |
| `users → appraiser_profiles` | 1:1 | Mỗi user có tối đa 1 hồ sơ Appraiser |
| `users → listings` | 1:N | User đăng nhiều sản phẩm |
| `stores → listings` | 1:N | Shop đăng nhiều sản phẩm |
| `categories → listings` | 1:N | Danh mục có nhiều sản phẩm |
| `listings → favorites` | 1:N | Listing được nhiều user yêu thích |
| `listings → appraisal_requests` | 1:N | Listing có thể yêu cầu thẩm định lại |
| `listings → price_predictions` | 1:N | Nhiều lần định giá theo thời gian |
| `listings → offers` | 1:N | Listing nhận nhiều offer |
| `offers → orders` | 1:1 | Offer được accept tạo 1 order |
| `orders → payments` | 1:1 | Mỗi order có 1 bản ghi payment |
| `orders → shipments` | 1:1 | Mỗi order có 1 shipment |
| `orders → disputes` | 1:N | Order có thể có tranh chấp |
| `orders → reviews` | 1:N | Buyer và seller đều có thể review |
| `orders → voucher_usages` | 1:1 | Mỗi order dùng tối đa 1 voucher |
| `vouchers → voucher_usages` | 1:N | Voucher được nhiều user dùng |
| `users → wallet_transactions` | 1:N | Mỗi biến động ví là 1 bản ghi |
| `users → chat_rooms` | N:M | Một cặp user có 1 room |
| `chat_rooms → chat_messages` | 1:N | Room có nhiều tin nhắn |
