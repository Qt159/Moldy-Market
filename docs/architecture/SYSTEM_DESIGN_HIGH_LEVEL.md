# Moldy Market — System Design: High Level

---

## 1. Tổng quan kiến trúc

Moldy Market được thiết kế theo kiến trúc **Modular Monolith** — một Spring Boot application duy nhất, chia thành các module có ranh giới nghiệp vụ rõ ràng. Cách này đơn giản để phát triển và deploy ở giai đoạn đầu, đồng thời có thể tách thành microservices sau khi traffic tăng.

### Nguyên tắc thiết kế

- Không dùng Redis hay message queue ở giai đoạn hiện tại; communication giữa modules được xử lý in-process. 
- Mỗi module chịu trách nhiệm một nhóm nghiệp vụ, không phụ thuộc trực tiếp vào implementation của nhau.
- **Escrow-first** — tất cả luồng tiền đi qua trạng thái tạm giữ trước khi giải ngân.
- Notification trên DynamoDB, ảnh trên S3 — không làm phình PostgreSQL.

---

## 2. Architecture Diagram


![HLD](docs/images/System_Design_High_Level.drawio.png)

---

## 3. Core Components

### Client Layer

| Client | Mô tả |
|---|---|
| **Web App** | React / Next.js. Giao diện chính cho Normal User, Store Owner, Appraiser |
| **Mobile App** | React Native. Tập trung vào mua sắm và theo dõi đơn hàng |
| **Admin Portal** | React, subdomain riêng. Chỉ Admin truy cập |

### API Gateway (Spring Cloud Gateway)

- TLS termination.
- Rate limiting theo IP.
- JWT validation ở tầng Gateway — giảm tải cho từng module.
- Routing `/api/**` → Spring Boot, `/ws/**` → WebSocket endpoint.

### Spring Boot Application

Một application duy nhất chứa tất cả modules. Modules giao tiếp với nhau qua **Spring Events** (in-process, synchronous) — không cần message broker bên ngoài.

### Data Layer

| Storage | Dữ liệu | Lý do |
|---|---|---|
| **PostgreSQL** | Toàn bộ dữ liệu nghiệp vụ | ACID, quan hệ, transaction |
| **DynamoDB** | Notifications | Workload đơn giản, scale độc lập, không cần join, TTL tự động |
| **Amazon S3** | Hình ảnh, tài liệu | Object storage, không làm phình DB |

---

## 4. Service Breakdown

```
com.moldy.moldymarket
├── auth/           # Đăng ký, đăng nhập, JWT, Google OAuth, OTP
├── user/           # Profile, địa chỉ, ngân hàng, Legit Points
├── listing/        # CRUD sản phẩm, upload ảnh, soft delete
├── search/         # Full-text search, bộ lọc, sort
├── offer/          # Gửi offer, counter-offer, trạng thái, hết hạn
├── order/          # Tạo đơn, lifecycle, hủy đơn, xác nhận nhận hàng
├── payment/        # Tích hợp VNPay/MoMo, Escrow logic, idempotency
├── shipping/       # Tích hợp shipping API, tracking
├── review/         # CRUD review, tính điểm uy tín
├── voucher/        # Tạo voucher, áp dụng, kiểm tra quota
├── wallet/         # Lịch sử ví, lệnh rút tiền, OTP 2FA
├── chat/           # WebSocket handler, lịch sử tin nhắn
├── notification/   # Ghi notification vào DynamoDB, gửi push qua AWS SNS
├── appraiser/      # Workflow thẩm định, conflict-of-interest check
├── store/          # Store CRUD, quản lý staff, store wallet
├── admin/          # Quản lý user, danh mục, tranh chấp, Legit Points
└── pricing/        # Auto-pricing engine, lưu price_predictions
```

### Giao tiếp giữa các modules

Thay vì message queue, các modules giao tiếp qua **Spring `ApplicationEventPublisher`** — publish/consume event trong cùng một JVM process.

Ví dụ:
- `OrderService` publish `OrderCreatedEvent` sau khi tạo đơn thành công.
- `NotificationModule` xử lý event này, tạo notification trong DynamoDB.
- `ShippingModule` xử lý để khởi tạo bản ghi vận chuyển.

Ưu điểm: không cần setup broker, dễ debug, đủ dùng cho giai đoạn đầu.

---

## 5. Important Design Decisions

### 5.1 Tại sao dùng Modular Monolith?

- Đơn giản deploy
- Chưa cần distributed-system phức tạp
- Có thể tách service sau
- Ranh giới giữa các module rõ

### 5.2 Tại sao dùng PostgreSQL?

- Order/payment/wallet cần transaction + consistency
- Relational data: Các entity có quan hệ rõ ràng và cần query/join nên PostgreSQL phù hợp với mô hình dữ liệu của hệ thống.


### 5.3 Tại sao dùng DynamoDB?

- Notification workload đơn giản
- Access pattern rõ: Mình biết trước ứng dụng sẽ đọc/ghi dữ liệu theo cách nào, nên có thể thiết kế DynamoDB table + partition key để phục vụ trực tiếp những truy vấn đó.
- TTL: Có thể tự động loại bỏ các notification cũ sau một khoảng thời gian mà không cần tự xử lý cleanup.

### 5.4 Tại sao dùng S3?

- Object storage
- Không đưa binary vào DB/backend: Client có thể upload trực tiếp lên S3 thông qua presigned URL, backend chỉ cần lưu object key/metadata trong database.

### 5.5 Tại sao dùng API GATEWAY?

- Single entry point: Client chỉ cần giao tiếp với một entry point thay vì gọi trực tiếp backend.
- Rate limiting: Giới hạn request từ client/IP trước khi request đi vào application.
- TLS: Tập trung xử lý HTTPS/TLS termination ở gateway.
- Routing: Định tuyến request đến đúng backend endpoint, ví dụ `/api/**` cho REST API và `/ws/**` cho WebSocket.

### 5.6 Tại sao dùng Events?

- Giảm coupling giữa các module: module phát sinh event không cần biết chi tiết module nào sẽ xử lý event đó.
- Các module có thể phản ứng độc lập: ví dụ OrderModule tạo order xong thì publish OrderCreatedEvent; NotificationModule nhận event để tạo notification, ShippingModule nhận event để khởi tạo shipment.
- In-process: sử dụng Spring ApplicationEventPublisher, event được xử lý ngay trong cùng Spring Boot application, không cần Kafka/RabbitMQ/SQS.
- Đơn giản hơn cho Modular Monolith: không phải vận hành thêm message broker khi hệ thống hiện tại chưa có nhu cầu distributed processing.
-Có thể chuyển sang message broker sau: nếu sau này tách module thành microservice hoặc cần xử lý asynchronous/scale độc lập, có thể thay cơ chế in-process bằng Kafka/RabbitMQ/SQS.


---

## 6. Security Design

### Authentication

| Cơ chế | Chi tiết |
|---|---|
| JWT Access Token | HS256, TTL 15 phút, chứa `{ userId, role, workspaceType }` |
| Refresh Token | Random UUID, lưu DB, TTL 7 ngày |
| 2FA rút tiền | OTP cho các thao tác nhạy cảm như rút tiền |
| Admin 2FA | Yêu cầu 2FA khi truy cập Admin Portal  |

### Authorization

```
ADMIN        → toàn quyền
STORE_OWNER  → quản lý gian hàng của mình
STORE_STAFF  → quyền được Store Owner gán
APPRAISER    → quyền thẩm định + Normal User
USER         → mua bán cá nhân
GUEST        → read-only (search, xem listing)
```

- Spring Security + `@PreAuthorize` kiểm tra role và ownership.
- Appraiser không được thẩm định sản phẩm của chính mình — kiểm tra `appraisalRequest.sellerId != currentUserId`.
- Store Staff không có quyền mua hàng và rút tiền — enforce tại service layer.

### Data Security

- Password được hash bằng BCrypt.
- Sensitive data như thông tin ngân hàng được mã hóa trước khi lưu.
- S3 access được kiểm soát bằng IAM và presigned URL.

---

## 7. Tech Stack Summary

### Backend

| Thành phần | Technology |
|---|---|
| Framework | Spring Boot 3.x |
| Language | Java 17+ |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security + JWT |
| WebSocket | Spring WebSocket (STOMP) |
| Internal Events | Spring `ApplicationEventPublisher` |
| Payment Integration | SePay (bank transfer webhook) |

### Frontend (đề xuất)

| Thành phần | Technology |
|---|---|
| Web | React + Next.js |
| Mobile | React Native (Expo) |
| State | TanStack Query |
| Realtime | SockJS + STOMP.js |
| Charts | Recharts |

### Infrastructure

| Thành phần | Technology |
|---|---|
| Primary DB | PostgreSQL 15 |
| NoSQL | AWS DynamoDB |
| Object Storage | AWS S3 |
| API Gateway | Spring Cloud Gateway |
| Container | Docker + Docker Compose |
| CI/CD | GitHub Actions |

---

*Tài liệu này là thiết kế mức cao (high level). Khi traffic tăng, các bước nâng cấp tiếp theo có thể là: thêm Redis cache cho search/biểu đồ giá, thay Spring Events bằng RabbitMQ/SQS cho async processing, tách Notification service riêng.*
