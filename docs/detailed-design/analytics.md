# Analytics Module - Seller Portal

## 1. Tổng Quan Analytics

Analytics không phải chỉ là một trang hiển thị vài biểu đồ, mà là một công cụ cung cấp dữ liệu chi tiết để Seller đưa ra các quyết định kinh doanh thông minh.

## 2. Cấu Trúc Analytics

Analytics được chia thành **4 nhóm chính**:

### A. Business Overview – Tổng Quan Kinh Doanh

Cung cấp cái nhìn toàn bộ về hiệu suất kinh doanh của cửa hàng.

#### Chỉ Số Tổng Quan (KPI Cards)


Total Revenue (Doanh thu từ đơn hàng hoàn tất) = Tổng giá trị Order ở trạng thái `COMPLETED` 
Total Orders (Tổng số đơn hàng) = Số Order của Seller 
Products Sold (Số sản phẩm đã bán) = Tổng quantity từ OrderItem hoàn tất 
Average Order Value (Giá trị đơn hàng trung bình) = Total Revenue / Total Orders 
Pending Orders (Đơn hàng chưa hoàn tất) = Số Order ở trạng thái `PENDING`, `CONFIRMED`, `PROCESSING` 
Refund Amount (Tổng tiền hoàn trả) = Tổng refund_amount từ Refund ở trạng thái `COMPLETED` 
Net Revenue (Doanh thu thực tế) = Total Revenue - Refund Amount 

#### Visualizations

1. **Revenue Trend Chart** (Line Chart)
   - Trục Y: Doanh thu (VNĐ)
   - Trục X: Thời gian (Ngày/Tuần/Tháng - tuỳ chọn)
   - Hiển thị Gross Sales vs Net Revenue

2. **Order Count Chart** (Bar Chart)
   - Hiển thị số lượng đơn hàng theo ngày/tuần/tháng

3. **Period Comparison**
   - So sánh doanh thu kỳ này vs kỳ trước
   - Hiển thị % thay đổi (tăng/giảm)

4. **Top 5 Best-Selling Products Table**
   - Tên sản phẩm
   - Số lượng bán
   - Doanh thu
   - Giá bán trung bình

---

### B. Revenue Analytics – Phân Tích Doanh Thu

Tập trung vào dòng tiền, hiệu suất bán hàng và trạng thái thanh toán.

#### 1. Revenue Trend
- Biểu đồ doanh thu theo **ngày**, **tuần**, **tháng** (người dùng chọn)
- Hiển thị cả **Gross Sales** (tổng giá trị) và **Net Revenue** (sau hoàn tiền)
- Tùy chọn lọc theo danh mục sản phẩm

#### 2. Revenue Breakdown
- Phân tích doanh thu theo **danh mục sản phẩm** (Donut/Pie Chart)
- Hiển thị:
  - Danh mục
  - Doanh thu
  - % so với tổng

#### 3. Monthly Comparison
- Bảng so sánh doanh thu giữa các tháng
- Cột: Tháng, Gross Sales, Refund, Net Revenue, AOV, % Change

#### 4. Transaction Summary
Tổng hợp trạng thái giao dịch:

| Trạng Thái | Ý Nghĩa | Số Tiền |
|-----------|---------|--------|
| **Completed** | Giao dịch hoàn tất | Tổng tiền đã thanh toán |
| **Pending Escrow** | Tiền đang giữ trong Escrow | Tổng escrow_amount chưa release |
| **Refunded** | Tiền đã hoàn lại khách | Tổng refund_amount |
| **Available Balance** | Số dư có thể rút ngay | Completed - Refunded |

**Quy Tắc Quan Trọng:**
- **Pending Escrow KHÔNG được cộng vào Available Balance**
- Pending Escrow là tiền đang "treo", chỉ release sau khi hoàn tất giao dịch
- Available Balance = Completed Transactions - Refunded Amount

#### 5. Net Revenue Calculation
```
NET REVENUE = Sales - Refund Amount - Platform Fee - Payment Processing Fee

- Sales = Tổng giá trị hàng hóa đã bán
- Refund Amount = Tiền hoàn lại khách
- Platform Fee = Phí nền tảng (nếu có)
- Payment Processing Fee = Phí thanh toán (nếu có)
- NET REVENUE (Tiền Seller thực sự nhận được)
```

---

### C. Product Performance – Hiệu Suất Sản Phẩm

Đây là phần quan trọng đối với Moldy Market, vì sản phẩm đồ cũ có giá trị và tình trạng khác nhau.

#### 1. Product Performance Table

**Best-Selling Products**: Top sản phẩm bán chạy để xác định mặt hàng có nhu cầu cao |
**Low-Performing Products**:  Sản phẩm ít bán để cân nhắc điều chỉnh giá hoặc ẩn sản phẩm 
**Product Views**: Lượt xem mỗi sản phẩm để đánh giá visibility
**Conversion Rate**: % chuyển đổi từ lượt xem thành đơn hàng để đánh giá hiệu quả listing 
**Average Selling Price** : Giá bán trung bình để So sánh với giá đăng ban đầu 


#### 2. Product Price Analytics 

Truy vết lịch sử giá bán của từng sản phẩm:

| Thông Tin | Ý Nghĩa |
|----------|---------|
| **Initial Price** | Giá đăng bán ban đầu |
| **Price Adjustments** | Lịch sử điều chỉnh giá (ngày/giờ + giá mới) |
| **Final Selling Price** | Giá bán thực tế khi hoàn tất |
| **Average Selling Price** | Giá trung bình của những sản phẩm đã bán |
| **Price Trend Chart** | Biểu đồ biến động giá theo thời gian |

**Ví Dụ Cụ Thể:**
```
iPhone 12 Pro Max 256GB
- 15/10/2024 10:30: Đăng bán với giá 13.000.000 VNĐ
- 20/10/2024 14:15: Giảm xuống 12.500.000 VNĐ
- 25/10/2024 09:45: Giảm tiếp 12.000.000 VNĐ
- 28/10/2024: Bán với giá 11.800.000 VNĐ (Final Price)

Analytics hiển thị:
- Biểu đồ giá: từ 13M → 12.5M → 12M → 11.8M
- So sánh: Giảm 9.2% so với giá ban đầu
- Đánh giá: Discount lớn có thể do nhu cầu, tình trạng sản phẩm
```

#### 3. Dữ Liệu Cần Lưu Trữ

Để thực hiện Product Price Analytics, Backend phải lưu:

**ProductPrice History (Bảng mới)**
```java
ProductPriceHistory {
  - id: UUID
  - productId: UUID
  - priceType: LISTED | ADJUSTED | SOLD (enum)
  - price: BigDecimal
  - changedAt: Timestamp
  - reason: String (optional - "Manual adjustment", "Auto discount", etc.)
  - changedBy: UUID (Seller ID)
}
```

**Product (Bảng hiện tại)**
```java
Product {
  - id: UUID
  - currentPrice: BigDecimal (giá hiện tại)
  - lastUpdatedAt: Timestamp
  // ... other fields
}
```

Khi Seller điều chỉnh giá, hệ thống:
1. Tạo record mới trong `ProductPriceHistory`
2. Cập nhật `currentPrice` trong `Product`
3. Analytics query lịch sử từ `ProductPriceHistory`

---

### D. Trust & Reputation – Uy Tín Người Bán

Đây là phần tạo nên sự khác biệt của Moldy Market.

#### Chỉ Số Uy Tín

**Order Completion Rate** = Completed Orders / Total Orders × 100 :  % đơn hàng được hoàn tất thành công 
**Cancellation Rate**=  Cancelled Orders / Total Orders × 100 : % đơn hàng bị hủy |
**Dispute Rate** = Disputed Orders / Total Orders × 100 : % đơn hàng có khiếu nại |
**Return/Refund Rate** = Refunded Orders / Total Orders × 100 : % đơn hàng được hoàn tiền 
**Average Seller Rating** = Trung bình rating từ reviews | 1-5 sao |
**Successful Transactions** = Số giao dịch hoàn tất thành công : con số

#### Hiển Thị Dữ Liệu

1. **Reputation Score Card**
   - Hiển thị điểm uy tín tổng (nếu có hệ thống scoring)
   - rating stars

2. **Performance Metrics Table**
   - Bảng tóm tắt các chỉ số trên
   - Trend indicator (tăng / giảm so với tháng trước)

3. **Dispute & Refund History**
   - Danh sách khiếu nại đã được xử lý
   - Kết quả xử lý (Approved / Rejected / Partial Refund)
   - Lý do khiếu nại
   - Ngày tạo & ngày xử lý

#### Quy tắc
- **Chỉ tính khiếu nại đã có kết luận hợp lệ** trong Dispute Rate
- **Không để khiếu nại chưa được xác minh làm giảm điểm uy tín Seller**
- Khiếu nại đang chờ xử lý hiển thị riêng, nhưng không ảnh hưởng metric chính thức
- Rating từ Review phải được xác thực từ Buyer có giao dịch hoàn tất

---

## 3. Flow Tổng Thể của Analytics

```
┌─────────────────────────────────────────────────────────────┐
│               Business Transactions (Data Source)            │
│  Order · Payment · Escrow · Refund · Product · Review      │
│              · Dispute · ProductPriceHistory                 │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                 Analytics Service Layer                      │
│         Aggregate · Calculate · Filter · Compare             │
│  - Order aggregation by date/category/status                │
│  - Revenue calculation (Gross, Net, Escrow)                 │
│  - Product performance metrics                              │
│  - Price history tracking                                   │
│  - Reputation score calculation                             │
└─────────────────────────┬───────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┬─────────────────┐
        ▼                 ▼                 ▼                 ▼
   ┌─────────┐    ┌──────────────┐   ┌──────────────┐   ┌──────────┐
   │ Business│    │   Revenue    │   │   Product    │   │  Trust & │
   │Overview │    │   Analytics  │   │ Performance  │   │Reputation│
   └─────────┘    └──────────────┘   └──────────────┘   └──────────┘
        │                 │                 │                 │
        └─────────────────┼─────────────────┴─────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                  Seller Dashboard UI                        │
│    Charts · KPI Cards · Tables · Date Filters · Export     │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Bảo Mật & Kiểm Soát Truy Cập

### Nguyên Tắc Cốt Lõi

**Seller chỉ được xem dữ liệu thuộc Store của mình**

### Quy Tắc Thực Hiện

1. **Xác Thực & Phân Quyền**
   - Mọi request phải có JWT token của Seller
   - Backend extract `storeId` từ authenticated user, KHÔNG tin `storeId` do Frontend truyền lên
   - Kiểm tra Seller có quyền quản lý Store này không

2. **Lọc Dữ Liệu**
   ```
   Query Analytics Data:
   1. Get storeId from authentication context
   2. Filter all queries by: WHERE order.store_id = authenticated_store_id
   3. Return only data belonging to that Store
   ```

3. **Logging & Audit**
   - Log mỗi truy cập Analytics (ai xem gì lúc nào)
   - Phát hiện anomalies (ví dụ: một Seller bất ngờ request quá nhiều data)

---

## 5. Chi Tiết API Endpoints

### 5.1 Business Overview

**GET /api/analytics/business-overview**

Query Parameters:
- `startDate`: ISO 8601 format (required)
- `endDate`: ISO 8601 format (required)
- `period`: `DAY` | `WEEK` | `MONTH` (default: `DAY`)

Response:
```json
{
  "kpiCards": {
    "totalRevenue": 123456789,
    "totalOrders": 45,
    "productsSold": 78,
    "averageOrderValue": 2743486,
    "pendingOrders": 3,
    "refundAmount": 5000000,
    "netRevenue": 118456789
  },
  "revenueTrend": [
    {
      "date": "2024-10-01",
      "grossSales": 5000000,
      "netRevenue": 4800000,
      "orderCount": 5
    },
    // ... more days
  ],
  "topProducts": [
    {
      "productId": "...",
      "productName": "iPhone 12",
      "quantitySold": 3,
      "revenue": 30000000,
      "avgPrice": 10000000
    },
    // ... top 5 products
  ],
  "periodComparison": {
    "currentPeriod": {
      "revenue": 50000000,
      "orders": 20
    },
    "previousPeriod": {
      "revenue": 45000000,
      "orders": 18
    },
    "changePercent": 11.11
  }
}
```

### 5.2 Revenue Analytics

**GET /api/analytics/revenue**

Query Parameters:
- `startDate`, `endDate`, `period` (as above)
- `categoryId` (optional)

Response:
```json
{
  "revenueTrend": [ /* as above */ ],
  "revenueBreakdown": [
    {
      "categoryId": "...",
      "categoryName": "Electronics",
      "revenue": 80000000,
      "percentage": 65.5
    }
  ],
  "monthlyComparison": [
    {
      "month": "2024-09",
      "grossSales": 100000000,
      "refundAmount": 5000000,
      "netRevenue": 95000000,
      "orderCount": 50,
      "aov": 1900000
    },
    {
      "month": "2024-10",
      "grossSales": 122000000,
      "refundAmount": 3800000,
      "netRevenue": 118200000,
      "orderCount": 45,
      "aov": 2711111
    }
  ],
  "transactionSummary": {
    "completed": 200000000,
    "pendingEscrow": 15000000,
    "refunded": 8000000,
    "availableBalance": 192000000
  }
}
```

### 5.3 Product Performance

**GET /api/analytics/products/performance**

Query Parameters:
- `startDate`, `endDate`
- `sortBy`: `SALES` | `REVENUE` | `VIEWS` | `CONVERSION` (default: `SALES`)
- `limit`: 10, 25, 50 (default: 10)

Response:
```json
{
  "products": [
    {
      "productId": "...",
      "productName": "iPhone 12 Pro Max",
      "views": 150,
      "ordersReceived": 5,
      "conversionRate": 3.33,
      "unitsSold": 5,
      "revenue": 50000000,
      "averageSellingPrice": 10000000,
      "initialPrice": 13000000,
      "currentPrice": 12000000,
      "status": "ACTIVE"
    }
  ]
}
```

### 5.4 Product Price History

**GET /api/analytics/products/{productId}/price-history**

Response:
```json
{
  "productId": "...",
  "productName": "iPhone 12 Pro Max",
  "priceHistory": [
    {
      "date": "2024-10-15T10:30:00Z",
      "priceType": "LISTED",
      "price": 13000000,
      "reason": "Initial listing"
    },
    {
      "date": "2024-10-20T14:15:00Z",
      "priceType": "ADJUSTED",
      "price": 12500000,
      "reason": "Manual adjustment"
    },
    {
      "date": "2024-10-25T09:45:00Z",
      "priceType": "ADJUSTED",
      "price": 12000000,
      "reason": "Price reduction"
    },
    {
      "date": "2024-10-28T16:20:00Z",
      "priceType": "SOLD",
      "price": 11800000,
      "reason": "Sold price"
    }
  ],
  "summary": {
    "highestPrice": 13000000,
    "lowestPrice": 11800000,
    "averagePrice": 12100000,
    "discountPercent": 9.23
  }
}
```

### 5.5 Trust & Reputation

**GET /api/analytics/reputation**

Response:
```json
{
  "metrics": {
    "orderCompletionRate": 98.5,
    "cancellationRate": 1.2,
    "disputeRate": 0.3,
    "returnRefundRate": 2.1,
    "averageRating": 4.7,
    "successfulTransactions": 197
  },
  "trends": {
    "currentMonth": {
      "completionRate": 98.5,
      "ratingAverage": 4.7
    },
    "previousMonth": {
      "completionRate": 97.2,
      "ratingAverage": 4.5
    }
  },
  "disputes": [
    {
      "disputeId": "...",
      "orderId": "...",
      "reason": "Product condition not as described",
      "status": "RESOLVED",
      "resolution": "PARTIAL_REFUND",
      "refundAmount": 2000000,
      "createdAt": "2024-10-20T10:30:00Z",
      "resolvedAt": "2024-10-22T14:15:00Z"
    }
  ]
}
```

---

## 6. Database Schema

### ProductPriceHistory (Bảng Mới)

```sql
CREATE TABLE product_price_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  price_type ENUM('LISTED', 'ADJUSTED', 'SOLD') NOT NULL,
  price DECIMAL(15, 2) NOT NULL,
  changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reason VARCHAR(255),
  changed_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  FOREIGN KEY (product_id) REFERENCES product(id),
  FOREIGN KEY (changed_by) REFERENCES user(id),
  INDEX (product_id, changed_at)
);
```

### Điều Chỉnh Product Table

```sql
ALTER TABLE product ADD COLUMN last_price_change_at TIMESTAMP;
```

---

## 7. Implementation Priority

### Phase 1: Foundation
1. Create ProductPriceHistory table
2. Update Product entity to track price changes
3. Implement Business Overview API

### Phase 2: Revenue
1. Implement Revenue Analytics API
2. Add Transaction Summary logic
3. Create visualizations for revenue trends

### Phase 3: Product Performance
1. Implement Product Performance API
2. Add Product Price History tracking
3. Calculate conversion rates

### Phase 4: Trust & Reputation
1. Implement Reputation API
2. Add dispute resolution tracking
3. Create rating aggregation

---

## 8. Frontend Components

### Layout Structure

```
Seller Portal
└── Analytics (Sidebar Menu)
    ├── Business Overview
    │   ├── KPI Cards Row
    │   ├── Revenue Trend Chart
    │   ├── Order Count Chart
    │   ├── Period Comparison
    │   └── Top 5 Products Table
    │
    ├── Revenue Analytics
    │   ├── Revenue Trend (Line Chart with toggle Gross/Net)
    │   ├── Revenue Breakdown (Pie/Donut Chart)
    │   ├── Monthly Comparison (Table)
    │   └── Transaction Summary (Status Cards)
    │
    ├── Product Performance
    │   ├── Product Table (sortable, filterable)
    │   ├── Product Detail Modal (click product row)
    │   │   ├── Price History Chart
    │   │   ├── Conversion Metrics
    │   │   └── Inventory Status
    │   └── Low-Performing Products Alert
    │
    └── Trust & Reputation
        ├── Reputation Score Card
        ├── Metrics Table
        ├── Dispute History Timeline
        └── Rating Distribution Chart
```

### Common UI Elements

- **Date Range Picker**: Select `startDate`, `endDate`
- **Period Selector**: Toggle DAY / WEEK / MONTH view
- **Export Button**: Export analytics data as CSV/PDF
- **Refresh Button**: Manual data refresh
- **Loading States**: Skeleton loaders while fetching

---

## 9. Performance Considerations

1. **Caching Strategy**
   - Cache analytics data for 15-30 minutes
   - Invalidate cache when new order/refund is created
   - Use Redis for quick retrieval

2. **Query Optimization**
   - Index on (store_id, created_at) for faster filtering
   - Aggregate daily/weekly data separately
   - Use database views for complex calculations

3. **Data Aggregation**
   - Pre-compute daily/monthly summaries in background job
   - Store aggregated data in separate analytics table
   - Query pre-computed data instead of raw transactions

4. **Pagination**
   - For large product lists, implement cursor-based pagination
   - Load products in batches (20-50 per load)

---

## 10. Security Checklist

- [ ] Backend validates authenticated Seller can access Store
- [ ] storeId extracted from JWT, not from request parameters
- [ ] All queries filtered by Store context
- [ ] Analytics endpoints require authentication
- [ ] Rate limiting on analytics endpoints (prevent abuse)
- [ ] Audit logging for data access
- [ ] Sensitive data (full order details) not exposed in analytics
