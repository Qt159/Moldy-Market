# Notification Module — Detailed Design

## Mục lục
1. [Tổng quan](#1-tổng-quan)
2. [Architecture Flow](#2-architecture-flow)
3. [Event Types](#3-event-types)
4. [DynamoDB Schema](#4-dynamodb-schema)
5. [Lambda Behavior](#5-lambda-behavior)
6. [API Endpoints](#6-api-endpoints)
7. [SES Email](#7-ses-email)
8. [SNS Push Notification](#8-sns-push-notification)
9. [Environment Variables](#9-environment-variables)
10. [AWS Infrastructure](#10-aws-infrastructure)
11. [Test Cases](#11-test-cases)

---

## 1. Tổng quan

Notification module xử lý tất cả thông báo trong hệ thống theo kiến trúc **event-driven**:

- Backend publish `NotificationEvent` lên **AWS SQS**
- **AWS Lambda** consume SQS, lưu vào **DynamoDB** và gửi email qua **AWS SES**
- User đọc notification qua **REST API** của backend

**SNS (Push Notification mobile)** chưa triển khai — planned khi có mobile app.

---

## 2. Architecture Flow

```
Backend (Spring Boot)
    │
    │ publishNotification(NotificationEvent)
    │ serialize JSON → SQS message
    ▼
AWS SQS — moldy-market-notifications
    │  (trigger Lambda khi có message)
    ▼
AWS Lambda — moldy-market-notification-handler
    │
    ├── Parse JSON → NotificationEvent
    │
    ├── DynamoDB PutItem
    │   PK = USER#{userId}
    │   SK = NOTIFICATION#{createdAt}#{notificationId}
    │
    └── SES SendEmail (nếu type cần email)

User
    │ GET /api/notifications
    ▼
Backend → DynamoDB Query
```

**Dead Letter Queue:** Message thất bại sau 3 lần retry → vào `moldy-market-notifications-dlq`.

---

## 3. Event Types

Định nghĩa trong `shared/NotificationType.java`. Backend chọn type phù hợp khi publish.

### Các type gửi email (SES)

| Type | Trigger |
|---|---|
| `ORDER_COMPLETED` | Đơn hàng hoàn tất, escrow giải ngân |
| `PAYMENT_SUCCESS` | Thanh toán thành công |
| `PAYMENT_FAILED` | Thanh toán thất bại |
| `ESCROW_RELEASED` | Escrow giải ngân cho seller |
| `ESCROW_REFUNDED` | Hoàn tiền cho buyer |
| `DISPUTE_OPENED` | Tranh chấp được mở |
| `DISPUTE_RESOLVED` | Tranh chấp được giải quyết |
| `WITHDRAWAL_SUCCESS` | Rút tiền thành công |
| `WITHDRAWAL_FAILED` | Rút tiền thất bại |

### Các type lưu DynamoDB (in-app only)

Tất cả type còn lại đều lưu DynamoDB nhưng không gửi email:
`ORDER_CREATED`, `ORDER_SHIPPED`, `ORDER_DELIVERED`, `OFFER_RECEIVED`, `OFFER_ACCEPTED`, `OFFER_REJECTED`, `OFFER_COUNTERED`, `OFFER_EXPIRED`, `APPRAISAL_*`, `REVIEW_RECEIVED`,...

---

## 4. DynamoDB Schema

**Table name:** `notifications`  

### Keys

| Attribute | Type | Giá trị |
|---|---|---|
| `PK` | String (Partition Key) | `USER#{userId}` |
| `SK` | String (Sort Key) | `NOTIFICATION#{createdAt}#{notificationId}` |

### Attributes

| Attribute | Type | Mô tả |
|---|---|---|
| `notificationId` | String | UUID, dùng cho idempotency check |
| `userId` | String | UUID của user nhận |
| `type` | String | Enum NotificationType |
| `referenceType` | String | `ORDER`, `OFFER`, `DISPUTE`, `APPRAISAL`, `WALLET` |
| `referenceId` | String | UUID của entity liên quan |
| `title` | String | Tiêu đề notification |
| `content` | String | Nội dung chi tiết |
| `isRead` | Boolean | Mặc định `false` |
| `createdAt` | String | ISO-8601 timestamp |
| `expiresAt` | Number | Unix epoch — TTL tự xóa sau 90 ngày |
| `deletedAt` | String | ISO-8601, set khi soft delete. Null nếu chưa xóa |

### Access Patterns

| Pattern | Query |
|---|---|
| Lấy tất cả notification của user (chưa xóa) | `PK = USER#{userId}`, filter `attribute_not_exists(deletedAt)` |
| Lấy theo notificationId | `PK = USER#{userId}`, filter `notificationId = {id}` |
| Đánh dấu đã đọc | `UpdateItem` SET `isRead = true` |
| Soft delete | `UpdateItem` SET `deletedAt = {now}` |
| Restore | `UpdateItem` REMOVE `deletedAt` |

### Idempotency

Lambda dùng `conditionExpression = "attribute_not_exists(PK) AND attribute_not_exists(SK)"` khi PutItem. SQS gửi lại cùng message → `ConditionalCheckFailedException` → bỏ qua, không ghi đè.

---

## 5. Lambda Behavior

**Function:** `moldy-market-notification-handler`  
**Runtime:** Java 17  
**Handler:** `com.moldy.lambda.notification.NotificationHandler`  
**Memory:** 512 MB  
**Timeout:** 30 giây

### Batch Processing

Lambda nhận batch tối đa 10 messages từ SQS. Mỗi message xử lý độc lập:

```
Với mỗi message trong batch:
  1. Parse JSON → NotificationEvent
     Fail → SKIP (non-retryable, log [SKIP])

  2. DynamoDB PutItem
     ConditionalCheckFailedException → bỏ qua (duplicate)
     DynamoDbException → throw RetryableException

  3. SES SendEmail (nếu type cần email)

  4. SNS Publish (chưa triển khai — bỏ qua nếu SNS_TOPIC_ARN null)

Nếu RetryableException → đưa messageId vào batchItemFailures
SQS chỉ retry đúng message thất bại (ReportBatchItemFailures bật)
```

### Log Format

| Prefix | Ý nghĩa |
|---|---|
| `[OK]` | Xử lý thành công |
| `[SKIP]` | JSON invalid, bỏ qua |
| `[DynamoDB] Duplicate skipped` | Message trùng, idempotency hoạt động |
| `[DynamoDB] ERROR` | Lỗi AWS DynamoDB — có statusCode và awsError |
| `[SES] Email sent` | Email gửi thành công |
| `[SES] Skip send email` | Không resolve được email recipient |
| `[RETRYABLE]` | Lỗi cần retry |
| `[ERROR]` | Lỗi không xác định |

---

## 6. API Endpoints

**Header (staging):** `X-User-Id: {userId}`  
**Header (production):** Lấy từ JWT via `@AuthenticationPrincipal`

### GET /api/notifications

Lấy danh sách notification có phân trang cursor-based.

**Query params:**
- `limit` — số item mỗi trang, mặc định 20, tối đa 50
- `token` — pagination cursor từ response trước, null cho trang đầu

**Response:**
```json
{
  "items": [
    {
      "notificationId": "uuid",
      "userId": "uuid",
      "type": "ORDER_COMPLETED",
      "referenceType": "ORDER",
      "referenceId": "uuid",
      "title": "Đơn hàng hoàn tất",
      "content": "...",
      "isRead": false,
      "createdAt": "2026-09-17T10:00:00Z",
      "deletedAt": null
    }
  ],
  "nextToken": "base64string hoặc null",
  "pageSize": 20
}
```

### GET /api/notifications/{id}

Lấy một notification theo notificationId.

**Response:** `NotificationRecord` (200) hoặc `404`

### PATCH /api/notifications/{id}/read

Đánh dấu đã đọc. Idempotent — gọi nhiều lần không lỗi.

**Response:** `204 No Content`

### DELETE /api/notifications/{id}

Soft delete — set `deletedAt`. Notification biến mất khỏi GET all.

**Response:** `204` hoặc `409` nếu đã xóa rồi

### PATCH /api/notifications/{id}/restore

Phục hồi notification đã soft delete.

**Response:** `204` hoặc `409` nếu chưa bị xóa

### Error Responses

| HTTP | Code | Khi nào |
|---|---|---|
| 404 | `NOT_FOUND` | notificationId không tồn tại |
| 409 | `CONFLICT` | Soft delete đã xóa / Restore chưa xóa |
| 400 | `BAD_REQUEST` | Pagination token không hợp lệ hoặc thuộc user khác |

---

## 7. SES Email

**Staging:** Cả sender và recipient phải được verify trong SES (sandbox mode).

**Env vars cần thiết:**
- `SES_SENDER_EMAIL` — email gửi đi (đã verify)
- `DEV_RECIPIENT_EMAIL` — email nhận (staging only, đã verify)

**Production:** `resolveEmail()` cần được update để lookup email thật từ User module theo `userId`. Hiện tại trả về `DEV_RECIPIENT_EMAIL` cố định.

**Email body** bao gồm: nội dung notification + type + referenceType/Id + timestamp.

---

## 8. SNS Push Notification

**Trạng thái: Chưa triển khai.**

Code đã có `SnsService.java` — nếu `SNS_TOPIC_ARN` env var không set thì tự động bỏ qua, không crash.

**Khi triển khai cần:**
1. Tạo SNS Topic
2. Tạo SNS Platform Application (FCM cho Android, APNs cho iOS)
3. Set `SNS_TOPIC_ARN` trong Lambda env
4. Update IAM role Lambda thêm `sns:Publish`
5. Update `resolveEmail()` → `resolveDeviceToken()` trong SnsService

---

## 9. Environment Variables

### Lambda

| Key | Giá trị ví dụ | Bắt buộc |
|---|---|---|
| `DYNAMODB_TABLE_NAME` | `notifications` | Có |
| `SES_SENDER_EMAIL` | `no-reply@example.com` | Có |
| `DEV_RECIPIENT_EMAIL` | `dev@example.com` | Staging only |
| `SNS_TOPIC_ARN` | `arn:aws:sns:...` | Không (planned) |
| `AWS_REGION` | Auto-inject bởi Lambda runtime | Không set thủ công |

### Backend

| Key | Giá trị ví dụ |
|---|---|
| `AWS_SQS_NOTIFICATION_QUEUE_URL` | `https://sqs.ap-south-1.amazonaws.com/...` |
| `AWS_DYNAMODB_NOTIFICATIONS_TABLE` | `notifications` |
| `AWS_REGION` | `ap-south-1` |

---

## 10. AWS Infrastructure

**Region:** `ap-south-1` (Mumbai)

| Service | Tên | Config |
|---|---|---|
| SQS | `moldy-market-notifications` | Standard queue, DLQ gắn maxReceiveCount=3 |
| SQS DLQ | `moldy-market-notifications-dlq` | Standard queue |
| DynamoDB | `notifications` | On-demand, TTL bật trên `expiresAt` |
| Lambda | `moldy-market-notification-handler` | Java 17, 512MB, 30s timeout |
| SES | Verified identities | Sandbox mode (staging) |

**Lambda trigger:** SQS `moldy-market-notifications`, batch size 10, **ReportBatchItemFailures bật**.

**IAM Role Lambda:** `moldy-market-notification-lambda-role`
- `dynamodb:PutItem`, `dynamodb:Query`, `dynamodb:UpdateItem` trên table `notifications`
- `ses:SendEmail`
- `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:GetQueueAttributes`
- `logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents`

---

## 11. Test Cases

### Phase 1 — Lambda (không cần chạy backend)

Gửi message thẳng qua **SQS Console → Send and receive messages**.

---

**TC-L01 — Happy path: message hợp lệ type gửi email**

Input:
```json
{
  "notificationId": "test-noti-001",
  "userId": "user-123",
  "type": "ORDER_COMPLETED",
  "referenceType": "ORDER",
  "referenceId": "order-abc",
  "title": "Đơn hàng hoàn tất",
  "content": "Đơn hàng #order-abc đã hoàn tất.",
  "createdAt": "2026-09-17T10:00:00Z"
}
```

Kỳ vọng:
- CloudWatch: `[OK] msgId=... notificationId=test-noti-001 type=ORDER_COMPLETED`
- CloudWatch: `[SES] Email sent to=... type=ORDER_COMPLETED`
- DynamoDB: item mới với `PK=USER#user-123`
- Email nhận được trong hộp thư

---

**TC-L02 — Happy path: type không gửi email**

Input: giống TC-L01, đổi `type` thành `OFFER_RECEIVED`.

Kỳ vọng:
- CloudWatch: `[OK]` — không có `[SES]`
- DynamoDB: item được ghi
- Không có email

---

**TC-L03 — JSON invalid (non-retryable)**

Input body: `{ invalid json }`

Kỳ vọng:
- CloudWatch: `[SKIP] Non-retryable parse error`
- Message không vào DLQ
- Lambda không crash

---

**TC-L04 — Duplicate message (idempotency)**

Gửi lại đúng body TC-L01.

Kỳ vọng:
- CloudWatch: `[DynamoDB] Duplicate skipped notificationId=test-noti-001`
- DynamoDB không tạo item mới
- Không gửi email lần 2

---

**TC-L05 — Batch nhiều messages**

Gửi 3 messages với `notificationId` khác nhau cùng lúc.

Kỳ vọng:
- 3 item được ghi vào DynamoDB
- CloudWatch có 3 dòng `[OK]`

---

### Phase 2 — API Backend (cần start backend)

Header: `X-User-Id: user-123`

---

**TC-A01 — GET all: trả về đúng danh sách**

```
GET /api/notifications
```

Kỳ vọng: 200, `items` chứa notification đã tạo ở Phase 1, `nextToken` null nếu ít hơn 20.

---

**TC-A02 — GET all: phân trang**

```
GET /api/notifications?limit=1
```

Kỳ vọng: 200, `items` có 1 phần tử, `nextToken` không null.

```
GET /api/notifications?limit=1&token={nextToken}
```

Kỳ vọng: 200, trang tiếp theo.

---

**TC-A03 — GET by ID: tìm thấy**

```
GET /api/notifications/test-noti-001
```

Kỳ vọng: 200, trả về đúng notification, `isRead: false`.

---

**TC-A04 — GET by ID: không tồn tại**

```
GET /api/notifications/not-exist
```

Kỳ vọng: 404 `{ "code": "NOT_FOUND", "message": "Notification not found: not-exist" }`

---

**TC-A05 — Mark as read**

```
PATCH /api/notifications/test-noti-001/read
```

Kỳ vọng: 204. GET by ID → `isRead: true`.

---

**TC-A06 — Mark as read lần 2 (idempotent)**

Gọi lại PATCH read.

Kỳ vọng: 204, không lỗi.

---

**TC-A07 — Soft delete**

```
DELETE /api/notifications/test-noti-001
```

Kỳ vọng: 204. GET all → notification không còn. GET by ID → vẫn trả về nhưng `deletedAt` không null.

---

**TC-A08 — Soft delete lần 2 (đã xóa rồi)**

```
DELETE /api/notifications/test-noti-001
```

Kỳ vọng: 409 `{ "code": "CONFLICT", "message": "Notification already deleted: ..." }`

---

**TC-A09 — Restore**

```
PATCH /api/notifications/test-noti-001/restore
```

Kỳ vọng: 204. GET all → notification xuất hiện lại, `deletedAt: null`.

---

**TC-A10 — Restore notification chưa xóa**

```
PATCH /api/notifications/test-noti-001/restore
```
(Sau khi đã restore)

Kỳ vọng: 409 `{ "code": "CONFLICT", "message": "Notification is not deleted: ..." }`

---

**TC-A11 — Pagination token của user khác**

```
GET /api/notifications?token={token của user-123}
Header: X-User-Id: user-456
```

Kỳ vọng: 400 `{ "code": "BAD_REQUEST", "message": "Pagination token does not belong to current user" }`

---

**TC-A12 — Pagination token invalid**

```
GET /api/notifications?token=notvalidbase64!!!
```

Kỳ vọng: 400 `{ "code": "BAD_REQUEST", "message": "Invalid pagination token" }`
