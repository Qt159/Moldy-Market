# Moldy Market

**Smart C2C Second-hand Marketplace**

Moldy Market là nền tảng thương mại điện tử **C2C (Consumer-to-Consumer)** dành cho việc mua bán các sản phẩm đã qua sử dụng.

Nền tảng hướng đến việc giúp người bán **đăng bán nhanh và định giá hợp lý**, đồng thời giúp người mua **tìm kiếm, đánh giá và giao dịch sản phẩm an toàn, minh bạch**.

Dự án gồm hai thành phần chính:

* **Backend:** cung cấp REST API và xử lý toàn bộ business logic của hệ thống.
* **Mobile Application:** ứng dụng dành cho người mua và người bán.

---

## 1. Problem Statement

Thị trường mua bán đồ cũ hiện nay vẫn tồn tại một số vấn đề:

* Người bán khó xác định mức giá phù hợp cho sản phẩm đã qua sử dụng.
* Việc đăng bán sản phẩm mất nhiều thời gian.
* Người mua khó đánh giá mức giá có hợp lý hay không.
* Độ tin cậy giữa người mua và người bán còn thấp.
* Các giao dịch trực tiếp thiếu cơ chế bảo vệ khi xảy ra tranh chấp.
* Việc thương lượng giá thường diễn ra thông qua chat và khó theo dõi.
* Thông tin vận chuyển và trạng thái giao dịch thường nằm ở nhiều nền tảng khác nhau.
* Người dùng khó theo dõi toàn bộ trạng thái của offer, thanh toán, vận chuyển và giao dịch trong một nơi.

Moldy Market giải quyết các vấn đề trên bằng cách kết hợp **smart pricing, structured negotiation, escrow payment, shipping integration và reputation system** trong cùng một nền tảng.

---

## 2. Core Features

### 2.1 Quick Listing

Người bán có thể tạo listing nhanh chóng bằng cách cung cấp:

* Hình ảnh sản phẩm.
* Danh mục.
* Tên và mô tả.
* Giá bán.
* Tình trạng sản phẩm.
* Khu vực giao dịch.
* Hình thức giao dịch.

---

### 2.2 Smart Pricing

Hệ thống hỗ trợ người bán tham khảo mức giá phù hợp dựa trên các thông tin về sản phẩm:

* Thời gian sử dụng.
* Tình trạng sản phẩm.
* Trạng thái bảo hành.
* Giá mua mới ban đầu.
* Dữ liệu giá của các sản phẩm tương tự trên thị trường.

Mục tiêu là cung cấp **price recommendation** bao gồm:

* Giá tham khảo.
* Khoảng giá hợp lý.
* Mức độ tin cậy của dự đoán.

Kết quả chỉ mang tính tham khảo và không tự động quyết định giá bán của người dùng.

---

### 2.3 Product Search & Discovery

Người mua có thể tìm kiếm sản phẩm thông qua nhiều tiêu chí:

* Danh mục.
* Khoảng giá.
* Tình trạng.
* Khu vực.
* Thời gian đăng bán.

Hệ thống hỗ trợ:

* Sort theo giá tăng/giảm.
* Sort theo thời gian đăng.
* Featured listings dựa trên các tiêu chí như độ phổ biến, mức độ tương tác hoặc chiến dịch quảng bá.
* Similar products dựa trên category, thuộc tính và mức giá tương đồng.
* Wishlist để người mua lưu các sản phẩm quan tâm.
* Notification khi xuất hiện sản phẩm phù hợp với wishlist.

---

### 2.4 Offer & Price Negotiation

Moldy Market cung cấp cơ chế **structured negotiation** thay vì chỉ thương lượng thông qua chat.

Người mua có thể:

* Gửi offer.
* Theo dõi trạng thái offer.
* Xem lịch sử thương lượng.

Người bán có thể:

* Accept offer.
* Reject offer.
* Counter offer.

Các offer có thời hạn và hệ thống tự động quản lý trạng thái hết hạn.

Mỗi offer được lưu thành một bản ghi riêng với trạng thái và thời gian tương ứng, giúp hệ thống theo dõi toàn bộ quá trình thương lượng.

Ví dụ:

Buyer → Offer 8M
Seller → Counter Offer 8.5M
Buyer → Counter Offer 8.2M
Seller → Accept

---

### 2.5 In-App Chat

Người mua và người bán có thể trao đổi trực tiếp trong ứng dụng.

Đặc điểm:

* Conversation gắn với listing và giữa buyer / seller.
* Lưu lịch sử tin nhắn.
* Hỗ trợ text message và image message.
* Push notification khi có tin nhắn mới.
* Tin nhắn được lưu trữ để phục vụ quá trình giao dịch và có thể được sử dụng làm dữ liệu tham chiếu khi xử lý tranh chấp.

---

### 2.6 Escrow Payment & Transaction Protection

Moldy Market mô phỏng cơ chế escrow để quản lý trạng thái thanh toán trong vòng đời giao dịch.

Luồng giao dịch cơ bản:

```text
Buyer places order
        ↓
Payment
        ↓
Money held in escrow
        ↓
Seller ships product
        ↓
Shipping status = Delivered
        ↓
Buyer confirms / confirmation period expires
        ↓
Release payment to Seller
```

Trong trường hợp xảy ra vấn đề, buyer hoặc seller có thể tạo **dispute** cho giao dịch.

Dispute có thể được sử dụng để:

* Ghi nhận lý do tranh chấp.
* Theo dõi trạng thái xử lý.
* Lưu bằng chứng liên quan.
* Cho phép admin xem xét và đưa ra quyết định.
* Thực hiện refund hoặc release payment tùy theo kết quả xử lý.

Hệ thống dự kiến hỗ trợ các payment gateway phổ biến tại Việt Nam như:

* VNPay
* MoMo

---

### 2.7 Voucher & Promotion

Hệ thống hỗ trợ voucher nhằm khuyến khích giao dịch.

Voucher có thể được phát hành bởi:

* **Moldy Market:** chương trình người dùng mới, lễ, khuyến mãi.
* **Seller:** voucher dành cho listing hoặc shop của người bán.

Các loại voucher:

* Percentage discount.
* Fixed amount discount.
* Minimum order value.
* Maximum discount.
* Usage limit.
* Validity period.
* Per-user usage limit.

Seller có thể theo dõi hiệu quả voucher thông qua dashboard.

---

### 2.8 Shipping Integration

Moldy Market tích hợp với các đơn vị vận chuyển để quản lý quá trình giao hàng.

Dự kiến hỗ trợ:

* GHN
* GHTK
* Viettel Post

Hệ thống hỗ trợ:

* Tạo shipping order.
* Tính phí vận chuyển.
* Theo dõi tracking number.
* Đồng bộ trạng thái vận chuyển.
* Webhook từ đơn vị vận chuyển.

Shipping status được sử dụng để đồng bộ với transaction lifecycle, đặc biệt trong quy trình escrow.

Ngoài hình thức giao hàng, hệ thống cũng hỗ trợ **meet-up / direct transaction**.

---

### 2.9 Reputation System

Mỗi người dùng có một **public reputation profile**.

Thông tin có thể bao gồm:

* Reputation score.
* Số giao dịch thành công.
* Reviews.
* Verification status.

Sau mỗi giao dịch, buyer và seller có thể đánh giá lẫn nhau.

Reputation score cũng có thể được sử dụng như một yếu tố trong việc **xếp hạng và ưu tiên listing**.

---

### 2.10 Market Price Analytics

Moldy Market cung cấp dữ liệu giá thị trường dựa trên các sản phẩm tương tự đã giao dịch.

Người mua có thể sử dụng dữ liệu này để:

* So sánh giá.
* Xác định sản phẩm đang có giá cao hay thấp hơn thị trường.

Người bán có thể sử dụng dữ liệu này để:

* Tham khảo giá trước khi đăng bán.
* Điều chỉnh giá bán phù hợp.

---
### 2.11 Notification

Hệ thống cung cấp thông báo cho người dùng về các sự kiện quan trọng như:

- Offer được tạo, chấp nhận hoặc từ chối.
- Đơn hàng thay đổi trạng thái.
- Thanh toán thành công hoặc thất bại.
- Shipment được cập nhật.
- Giao dịch hoàn tất hoặc có tranh chấp.
- Các hoạt động liên quan đến listing.

Notification được xử lý độc lập với core transactional data nhằm giảm tải cho hệ thống giao dịch và hỗ trợ xử lý thông báo bất đồng bộ.

### 2.12 Dashboard & Analytics

#### Admin Dashboard

Admin có thể theo dõi:

* User growth.
* Listing growth.
* Successful / failed transactions.
* Transaction volume.
* Platform revenue.
* Refund & dispute rate.
* Popular categories.
* Average transaction price.
* Average time-to-sell.
* Offer success rate.
* Voucher performance.

#### User Dashboard

Seller có thể theo dõi:

* Active / sold listings.
* Revenue.
* Listing views.
* Popular listings.
* Offer acceptance rate.
* Voucher performance.

Buyer có thể theo dõi:

* Purchase history.
* Total spending.
* Completed transactions.

Người dùng cũng có thể xem sự thay đổi reputation theo thời gian.

---

## 3. Future Extension

### AI Assistant

Moldy Market có thể mở rộng với AI Assistant để hỗ trợ người dùng.

Các hướng phát triển dự kiến:

**In-app Assistance**

Giúp người dùng tìm nhanh chức năng:

* Cách tạo listing.
* Cách tạo voucher.
* Cách xem offer history.
* Cách xử lý giao dịch.

**Buying & Selling Assistance**

AI có thể hỗ trợ:

* Gợi ý cách đăng bán sản phẩm.
* Hỗ trợ mô tả tình trạng sản phẩm.
* Gợi ý danh mục.
* Hỗ trợ tìm kiếm sản phẩm theo nhu cầu tự nhiên của người dùng.

---

## 4. System Scope

Moldy Market tập trung vào các bài toán backend có tính chất **transactional, distributed và event-driven**, bao gồm:

* Marketplace management.
* Product search and discovery.
* Price recommendation.
* Offer negotiation.
* Order and transaction lifecycle.
* Payment and transaction protection.
* Shipping integration.
* In-app chat.
* Notification.
* Reputation.
* Analytics.
* External service integration.

---

## 5. Technology Stack

> Stack sẽ được cập nhật khi architecture được chốt.

### Backend

* Java
* Spring Boot
* Spring Security
* Spring Data JPA
* PostgreSQL
* RESTful API

### Data & Storage

* PostgreSQL — core transactional data
* Amazon DynamoDB — notification data
* Amazon S3 — image/object storage

### Infrastructure

* AWS
* Docker
* CI/CD


### External Services

* Payment Gateway
* Shipping Provider


### Mobile

* Mobile application for Buyer / Seller

---

## 6. Documentation

Detailed project documentation:

* [Requirements](docs/requirements/)
* [Use Cases](docs/use-cases/)
* [Architecture](docs/architecture/)
* [Database Design](docs/database/)
* [API Documentation](docs/api/)
* [Deployment](docs/deployment/)

---

## 7. Project Status

🚧 **Under Development**

The project is currently in the design and implementation phase.
