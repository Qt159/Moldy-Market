# S3 File Upload Flow – Moldy Market

## 1. Overview

Moldy Market sử dụng **Amazon S3** để lưu trữ các tệp tin được người dùng tải lên trong quá trình sử dụng hệ thống.

Các loại file dự kiến:

Product: Ảnh sản phẩm
Appraisal: Ảnh hiện trạng, tài liệu và báo cáo thẩm định PDF
Dispute: Ảnh, video và tài liệu bằng chứng khiếu nại
User: Avatar
Order: Hóa đơn liên quan

### 1.1. Architecture

Sử dụng **Presigned URL** để Frontend upload file trực tiếp lên S3.

```text
                 1. Request upload
Frontend  ------------------------------> Backend
                                             |
                                             | 2. Validate request
                                             | 3. Generate Presigned URL
                                             |
Frontend  <------------------------------ Backend
                 4. Return URL
    |
    | 5. PUT file directly to S3
    |
    v
 Amazon S3
    |
    | 6. Upload completed
    |
Frontend
    |
    | 7. Confirm upload
    v
 Backend
    |
    | 8. Verify object using HeadObject
    | 9. Update StoredFile status
    v
 Database
```

Backend chịu trách nhiệm kiểm soát quyền truy cập, tạo URL và quản lý metadata. S3 chịu trách nhiệm lưu trữ nội dung file.

---

## 2. Lý do dùng Presigned URL

### 2.1. Các phương án upload

Có ba phương án phổ biến để upload file lên Amazon S3.

#### Approach 1: Upload through Backend

Frontend gửi file đến Spring Boot bằng `multipart/form-data`. Backend nhận file và sử dụng AWS SDK để upload lên S3.

```text
Frontend
   |
   | MultipartFile
   v
Spring Boot Backend
   |
   | PutObject
   v
Amazon S3
```

**Advantages:**
- Frontend không cần biết về AWS.
- Backend kiểm soát toàn bộ quá trình upload.
- Có thể kiểm tra file trước khi upload.
- Phù hợp với các file nhỏ hoặc nghiệp vụ cần xử lý file ngay tại Backend.

**Disadvantages:**
- Backend phải nhận và truyền toàn bộ dữ liệu file.
- Tốn tài nguyên RAM, CPU và network.
- Khi có nhiều người upload đồng thời, Backend có thể trở thành bottleneck.
- Tăng thời gian xử lý request và áp lực lên EC2.

#### Approach 2: Presigned URL

Backend tạo một URL có chữ ký bằng AWS SDK. Frontend sử dụng URL này để upload trực tiếp lên S3.

```text
Frontend
   |
   | Request Presigned URL
   v
Backend
   |
   | Generate Presigned URL
   v
Frontend
   |
   | Direct PUT
   v
Amazon S3
```

**Advantages:**
- Backend không phải truyền dữ liệu file.
- Giảm tải cho Spring Boot và EC2.
- Upload trực tiếp lên S3, phù hợp với file có dung lượng lớn.
- Hỗ trợ nhiều người upload đồng thời tốt hơn về mặt kiến trúc.
- Frontend không cần lưu AWS Access Key hoặc Secret Key.
- Backend vẫn kiểm soát việc cấp quyền upload thông qua URL có thời hạn.

**Disadvantages:**
- Cần triển khai thêm bước xác nhận upload.
- Phải cấu hình CORS cho S3.
- URL có thể bị sử dụng bởi bất kỳ ai sở hữu nó trong thời gian còn hiệu lực.
- Backend cần kiểm tra file sau khi upload, không thể chỉ tin vào thông tin do Frontend gửi lên.

#### Approach 3: Frontend upload using AWS Credentials

Frontend được cấp AWS credentials tạm thời và tự gọi S3 SDK để upload.

**Advantages:**
- Upload trực tiếp lên S3.
- Có thể sử dụng IAM policy để giới hạn quyền truy cập.
- Không cần Backend đứng giữa quá trình upload.

**Disadvantages:**
- Cần triển khai cơ chế cấp và quản lý temporary credentials.
- Tăng độ phức tạp trong quản lý IAM và danh tính người dùng.
- Không phù hợp để nhúng AWS Access Key/Secret Key cố định vào Frontend.


### 2.2. Decision

Moldy Market lựa chọn **Presigned URL** làm cơ chế upload mặc định.

Lý do:

1. Hệ thống có nhiều nghiệp vụ cần upload file như Product, Appraisal và Dispute.
2. Một số file có thể có dung lượng lớn, đặc biệt là ảnh và tài liệu PDF.
3. Không muốn Backend Spring Boot trở thành trung gian truyền dữ liệu file.
4. Muốn giảm tải cho EC2 và dành tài nguyên Backend cho xử lý nghiệp vụ.
5. Vẫn duy trì được cơ chế kiểm soát quyền upload thông qua Backend.

**Kết luận:** Backend quản lý quyền; Frontend truyền file; Amazon S3 lưu trữ file.

---

## 3. AWS S3 Configuration

### Step 1: Create S3 Bucket

Tạo một S3 bucket trên AWS Console.

Ví dụ: moldy-market-storage

Các cấu hình cần thiết:

| Configuration | Value |
|---|---|
| AWS Region | Cùng Region với EC2 nếu có thể |
| Object Ownership | Bucket owner enforced |
| Block Public Access | Enabled |
| Versioning | Có thể bật khi cần quản lý phiên bản |
| Default Encryption | SSE-S3 |
| Public Access | Disabled |

**Important:**

Không public bucket để Frontend có thể truy cập trực tiếp. Các thao tác upload và download phải được cấp quyền thông qua Backend.

### Step 2: Configure CORS

Frontend chạy trên domain riêng nên cần cấu hình CORS để trình duyệt cho phép gửi request trực tiếp đến S3.

Ví dụ:

```json
[
  {
    "AllowedHeaders": [
      "Content-Type",
      "Content-Length"
    ],
    "AllowedMethods": [
      "PUT",
      "GET",
      "HEAD"
    ],
    "AllowedOrigins": [
      "http://localhost:5173",
      "https://your-frontend-domain.com"
    ],
    "ExposeHeaders": [
      "ETag"
    ],
    "MaxAgeSeconds": 3000
  }
]
```

Thay domain bằng domain thực tế của Frontend khi deploy.

CORS chỉ kiểm soát request từ trình duyệt. Nó không thay thế cơ chế xác thực và phân quyền của S3.

### Step 3: Configure IAM Role

Backend chạy trên EC2 nên sử dụng **IAM Role gắn với EC2 Instance** thay vì hardcode AWS credentials trong source code.

IAM policy cần giới hạn quyền trên bucket của hệ thống.

Ví dụ:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::moldy-market-storage/*"
    }
  ]
}
```

`HeadObject` được cấp quyền thông qua `s3:GetObject`.

Có thể bổ sung quyền `s3:ListBucket` nếu Backend thực sự cần liệt kê object.

**Security notes:**
- Không cấp `s3:*` nếu không cần thiết.
- Không cấp quyền public access.
- Không lưu Access Key/Secret Key trong GitHub.
- Dùng IAM Role để AWS SDK tự lấy credentials từ môi trường EC2.

---

## 4. Backend Implementation

### Step 1: Add AWS SDK Dependencies

Sử dụng AWS SDK for Java v2.

Nếu project đang quản lý dependency bằng Maven:

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
```

Presigned URL cần thêm:

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3-presigner</artifactId>
</dependency>
```

Quản lý version thông qua AWS SDK BOM để tránh lệch version giữa các module.

### Step 2: Configure S3 Client

Tạo configuration để khởi tạo `S3Client` và `S3Presigner`.

```java
@Configuration
public class S3Config {

    @Value("${aws.s3.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(
                    DefaultCredentialsProvider.create()
                )
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(
                    DefaultCredentialsProvider.create()
                )
                .build();
    }
}
```

`DefaultCredentialsProvider` tự tìm credentials theo AWS credential provider chain. Khi deploy trên EC2, SDK có thể sử dụng IAM Role của Instance.

### Step 3: Configure Application Properties

```yaml
aws:
  s3:
    bucket: moldy-market-storage
    region: ap-southeast-1
    presigned-url-expiration: 5
```

Giá trị expiration ở trên tính bằng phút.

Không commit thông tin credentials vào file cấu hình.

### Step 4: Design Storage Package

Đề xuất cấu trúc:

```text
common/
└── storage/
    ├── config/
    │   └── S3Config.java
    │
    ├── controller/
    │   └── FileController.java
    │
    ├── service/
    │   ├── FileStorageService.java
    │   └── impl/
    │       └── S3FileStorageService.java
    │
    ├── dto/
    │   ├── PresignedUrlRequest.java
    │   ├── PresignedUrlResponse.java
    │   └── FileResponse.java
    │
    ├── entity/
    │   └── StoredFile.java
    │
    ├── enums/
    │   ├── FilePurpose.java
    │   └── FileStatus.java
    │
    └── repository/
        └── StoredFileRepository.java
```

Các module Product, Appraisal và Dispute chỉ gọi `FileStorageService`, không tự triển khai logic AWS S3.

### Step 5: Design StoredFile Entity

Tạo entity để quản lý metadata của file.

Các trường đề xuất:

| Field | Type | Description |
|---|---|---|
| id | UUID | File ID |
| ownerId | UUID | Người upload |
| objectKey | String | Đường dẫn object trong S3 |
| originalFilename | String | Tên file ban đầu |
| contentType | String | MIME type |
| fileSize | Long | Dung lượng |
| purpose | Enum | Mục đích sử dụng |
| status | Enum | Trạng thái file |
| createdAt | LocalDateTime | Thời gian tạo |
| uploadedAt | LocalDateTime | Thời gian upload thành công |

`objectKey` là định danh object trên S3. Không nên sử dụng trực tiếp tên file người dùng gửi lên làm object key.

#### FilePurpose

```java
public enum FilePurpose {
    PRODUCT_IMAGE,
    APPRAISAL_EVIDENCE,
    APPRAISAL_REPORT,
    DISPUTE_EVIDENCE,
    USER_AVATAR,
    ORDER_DOCUMENT
}
```

#### FileStatus

```java
public enum FileStatus {
    PENDING,
    UPLOADED,
    PROCESSING,
    AVAILABLE,
    REJECTED,
    DELETED
}
```

Ý nghĩa:

- `PENDING`: Đã tạo metadata và cấp URL nhưng chưa xác nhận upload.
- `UPLOADED`: File đã được upload lên S3.
- `PROCESSING`: File đang được kiểm tra hoặc xử lý bất đồng bộ.
- `AVAILABLE`: File đã qua kiểm tra và được phép sử dụng.
- `REJECTED`: File không hợp lệ.
- `DELETED`: File đã bị xóa theo nghiệp vụ.

Trong phiên bản đầu, có thể chuyển trực tiếp từ `PENDING` sang `AVAILABLE` sau khi xác minh và kiểm tra file. Khi bổ sung xử lý bất đồng bộ, sử dụng `UPLOADED → PROCESSING → AVAILABLE`.

### Step 6: Define Object Key Structure

Không lưu file theo tên gốc do người dùng cung cấp.

Đề xuất cấu trúc:

```text
products/{productId}/{uuid}.jpg

appraisals/{appraisalId}/evidence/{uuid}.jpg

appraisals/{appraisalId}/reports/{uuid}.pdf

disputes/{disputeId}/{uuid}.jpg

users/{userId}/avatar/{uuid}.jpg

orders/{orderId}/{uuid}.pdf
```

Ví dụ:

```text
products/550e8400-e29b-41d4-a716-446655440000/a8f2....jpg
```

Lợi ích:
- Tránh trùng tên file.
- Dễ quản lý theo nghiệp vụ.
- Hạn chế việc đoán đường dẫn file.
- Thuận tiện cho việc phân quyền và cleanup.

Lưu ý: Object key không phải cơ chế phân quyền. Backend vẫn phải kiểm tra quyền truy cập của người dùng đối với file.

---

## 5. API Design

### Step 1: Request Presigned URL

Endpoint:

```http
POST /api/files/presigned-url
```

Request:

```json
{
  "filename": "product-image.jpg",
  "contentType": "image/jpeg",
  "fileSize": 2048000,
  "purpose": "PRODUCT_IMAGE"
}
```

Backend thực hiện:

1. Xác thực người dùng.
2. Kiểm tra quyền upload theo nghiệp vụ.
3. Validate filename, MIME type và file size.
4. Generate UUID cho object key.
5. Tạo `StoredFile` với trạng thái `PENDING`.
6. Generate Presigned PUT URL.
7. Trả URL và file ID cho Frontend.

Response:

```json
{
  "fileId": "uuid",
  "uploadUrl": "https://bucket.s3.amazonaws.com/...",
  "objectKey": "products/product-id/uuid.jpg",
  "expiresIn": 300
}
```

Không nhận `objectKey` tùy ý từ Frontend. Backend phải tự tạo và kiểm soát object key.

### Step 2: Upload File Directly to S3

Frontend sử dụng URL được Backend cấp để upload file.

Ví dụ với Axios:

```javascript
await axios.put(uploadUrl, file, {
    headers: {
        "Content-Type": file.type
    }
});
```

Đây là request trực tiếp đến S3, không gửi file qua Spring Boot.

Frontend cần gửi đúng các header đã được ký trong Presigned URL.

### Step 3: Confirm Upload

Endpoint:

```http
POST /api/files/{fileId}/confirm
```

Backend thực hiện:

1. Tìm `StoredFile` theo `fileId`.
2. Kiểm tra file thuộc về người dùng hiện tại hoặc người dùng có quyền thực hiện nghiệp vụ.
3. Kiểm tra trạng thái hiện tại là `PENDING`.
4. Gọi S3 `HeadObject` để xác nhận object tồn tại.
5. Kiểm tra dung lượng và metadata thực tế.
6. Cập nhật trạng thái file.

Response:

```json
{
  "fileId": "uuid",
  "status": "AVAILABLE",
  "message": "File uploaded successfully"
}
```

**Important:**

Không được tin hoàn toàn vào `fileSize` hoặc `contentType` do Frontend gửi khi tạo Presigned URL.

`HeadObject` giúp kiểm tra sự tồn tại và metadata của object nhưng không chứng minh nội dung file thực sự đúng định dạng. Nếu cần kiểm tra sâu, phải đọc nội dung file để xác minh magic bytes hoặc sử dụng quy trình scan riêng.

### Step 4: Generate Download URL

Không public bucket để người dùng tự truy cập object.

Khi người dùng cần xem file, Backend kiểm tra quyền truy cập và tạo Presigned GET URL.

Endpoint:

```http
GET /api/files/{fileId}/download-url
```

Backend:

1. Tìm file theo ID.
2. Kiểm tra trạng thái file.
3. Kiểm tra quyền xem file theo nghiệp vụ.
4. Generate Presigned GET URL.
5. Trả URL cho Frontend.

URL download nên có thời hạn ngắn.

---

## 6. Frontend Upload Flow

Quy trình upload từ Frontend:

```text
1. User selects file
       |
       v
2. Validate basic file information
       |
       v
3. POST /api/files/presigned-url
       |
       v
4. Receive fileId and uploadUrl
       |
       v
5. PUT file directly to S3
       |
       v
6. POST /api/files/{fileId}/confirm
       |
       v
7. Receive final file status
       |
       v
8. Use fileId in business request
```

Ví dụ:

Khi Seller tạo sản phẩm:

- Frontend upload ảnh sản phẩm.
- Backend trả về danh sách `fileId`.
- Frontend gửi `fileId` trong request tạo Product.
- Backend kiểm tra các file thuộc quyền sử dụng của Seller và đúng mục đích `PRODUCT_IMAGE`.
- Backend lưu quan hệ giữa Product và StoredFile.

Không nên gửi URL Presigned đã hết hạn hoặc URL upload làm thông tin tham chiếu lâu dài. Lưu `fileId` hoặc `objectKey` trong database.

---

## 7. Database Relationship

Có thể sử dụng bảng trung gian để liên kết file với các nghiệp vụ.

Ví dụ:

```text
StoredFile
    |
    | 1 - N
    v
ProductImage
    |
    v
Product
```

Với các module khác:

```text
StoredFile
    |
    +---- AppraisalAttachment
    |
    +---- DisputeAttachment
    |
    +---- OrderDocument
```

Các bảng liên kết lưu `fileId` và ID nghiệp vụ tương ứng.

Cách này giúp `StoredFile` dùng chung cho toàn hệ thống, tránh tạo nhiều bảng metadata file riêng biệt cho từng module.

---

## 8. Security Considerations

### 8.1. File Validation

Backend cần validate:

- Allowed MIME types.
- Maximum file size.
- File extension.
- Magic bytes để xác minh định dạng thực tế.
- Tên file và object key.
- Mục đích sử dụng file.

Ví dụ allowlist:

| Purpose | Allowed Types |
|---|---|
| PRODUCT_IMAGE | JPEG, PNG, WebP |
| APPRAISAL_EVIDENCE | JPEG, PNG, WebP |
| APPRAISAL_REPORT | PDF |
| DISPUTE_EVIDENCE | JPEG, PNG, WebP, PDF |
| USER_AVATAR | JPEG, PNG, WebP |

Giới hạn dung lượng phải được định nghĩa theo từng purpose và cấu hình thống nhất giữa Frontend và Backend.

### 8.2. Authorization

- Chỉ người dùng đã xác thực mới được yêu cầu Presigned URL.
- Kiểm tra quyền upload theo từng nghiệp vụ.
- Chỉ chủ sở hữu hoặc người có quyền mới được xem file.
- Không cho phép người dùng tùy ý thay đổi object key.
- Kiểm tra quyền sử dụng file trước khi liên kết với Product, Appraisal hoặc Dispute.

### 8.3. Presigned URL Expiration

- Upload URL nên có thời hạn ngắn, ví dụ 5 phút.
- Download URL cũng nên có thời hạn ngắn.
- Không lưu URL Presigned làm URL file cố định.
- Không ghi đầy đủ URL có chữ ký vào application logs.

### 8.4. Prevent Orphan Files

Một số trường hợp file được upload lên S3 nhưng Frontend không gọi API confirm hoặc nghiệp vụ tạo Product thất bại.

Cần có cơ chế cleanup:

- Tìm các file `PENDING` quá thời gian quy định.
- Kiểm tra trạng thái object.
- Xóa object không còn được sử dụng.
- Cập nhật trạng thái metadata tương ứng.

Có thể triển khai bằng `@Scheduled` trong giai đoạn đầu.

---

## 9. Optional: Asynchronous File Processing

Không cần sử dụng SQS chỉ để upload file.

Presigned URL đã giải quyết việc truyền file trực tiếp lên S3. SQS chỉ nên được bổ sung khi cần xử lý sau upload.

Ví dụ:

```text
Frontend
   |
   | Direct Upload
   v
Amazon S3
   |
   | S3 Event Notification
   v
Amazon SQS
   |
   v
Worker / Lambda
   |
   +---- Validate file
   |
   +---- Virus scanning
   |
   +---- Generate thumbnail
   |
   +---- Extract metadata
   |
   v
Update StoredFile Status
```

Trong trường hợp này:

```text
PENDING
   |
   v
UPLOADED
   |
   v
PROCESSING
   |
   +---- Success ----> AVAILABLE
   |
   +---- Failed -----> REJECTED
```

Cần đảm bảo worker xử lý idempotent vì S3 Event Notification có thể được gửi nhiều lần. Nên có DLQ để xử lý các message thất bại liên tục.

Đây là hướng mở rộng, chưa cần triển khai ngay trong phiên bản đầu.

---

## 10. Error Handling

Một số trường hợp cần xử lý:

| Scenario | Expected behavior |
|---|---|
| Unsupported file type | Reject request |
| File exceeds size limit | Reject request |
| User unauthorized | Return 403 |
| File metadata not found | Return 404 |
| Presigned URL expired | Request a new URL |
| S3 upload failed | Keep or expire PENDING record |
| Confirm called before upload | Return appropriate error |
| Object does not exist | Reject confirmation |
| User confirms another user's file | Return 403 |
| File already confirmed | Handle idempotently |
| Business creation failed | Cleanup unlinked files when appropriate |

Không nên tự động chuyển trạng thái file sang `AVAILABLE` chỉ vì Frontend gọi API confirm thành công.

---

## 11. Testing Checklist

### AWS Configuration

- [ ] S3 bucket được tạo thành công.
- [ ] Block Public Access được bật.
- [ ] IAM Role được gắn vào EC2.
- [ ] Backend truy cập S3 bằng IAM Role.
- [ ] CORS hoạt động với Frontend.
- [ ] Không có AWS credentials trong source code.

### Backend

- [ ] Generate Presigned PUT URL.
- [ ] Tạo StoredFile với trạng thái PENDING.
- [ ] Kiểm tra MIME type.
- [ ] Kiểm tra file size.
- [ ] Generate object key bằng UUID.
- [ ] Confirm upload bằng HeadObject.
- [ ] Kiểm tra quyền sở hữu file.
- [ ] Generate Presigned GET URL.
- [ ] Cleanup file không được sử dụng.

### Frontend

- [ ] Upload ảnh thành công.
- [ ] Upload PDF thành công.
- [ ] Hiển thị upload progress.
- [ ] Xử lý URL hết hạn.
- [ ] Xử lý upload thất bại.
- [ ] Gọi API confirm sau upload.
- [ ] Sử dụng fileId khi tạo nghiệp vụ.

### Security

- [ ] Không truy cập được bucket public.
- [ ] Không upload file vượt quá giới hạn.
- [ ] Không upload file sai định dạng.
- [ ] Không sử dụng file của người dùng khác.
- [ ] Không download file khi không có quyền.
- [ ] URL hết hạn không thể tiếp tục sử dụng.

---

## 12. Implementation Roadmap

Nên triển khai theo từng giai đoạn để tránh xây dựng quá nhiều thứ ngay từ đầu.

### Phase 1: Basic S3 Upload

- [ ] Tạo S3 bucket.
- [ ] Cấu hình IAM Role.
- [ ] Cấu hình CORS.
- [ ] Thêm AWS SDK v2.
- [ ] Tạo S3Client và S3Presigner.
- [ ] Tạo StoredFile entity.
- [ ] Implement FileStorageService.
- [ ] Implement API tạo Presigned URL.
- [ ] Implement API confirm upload.
- [ ] Test upload và download.

### Phase 2: Business Integration

- [ ] Tích hợp Product Image.
- [ ] Tích hợp Appraisal Evidence.
- [ ] Tích hợp Appraisal Report.
- [ ] Tích hợp Dispute Evidence.
- [ ] Tích hợp User Avatar.
- [ ] Kiểm tra quyền sử dụng file theo từng module.

### Phase 3: Security and Optimization

- [ ] Validate MIME type và magic bytes.
- [ ] Kiểm tra dung lượng file thực tế.
- [ ] Cleanup file không được sử dụng.
- [ ] Tối ưu upload nhiều file.
- [ ] Bổ sung monitoring và logging.

### Phase 4: Asynchronous Processing (Future)

- [ ] S3 Event Notification.
- [ ] SQS.
- [ ] Worker hoặc Lambda.
- [ ] Virus scanning.
- [ ] Image thumbnail generation.
- [ ] Metadata extraction.
- [ ] DLQ và idempotency.

---

## 13. Final Architecture Decision

Moldy Market sử dụng:

| Component | Responsibility |
|---|---|
| Spring Boot | Authentication, authorization, validation, URL generation, metadata management |
| Amazon S3 | File storage |
| S3Presigner | Generate Presigned URLs |
| S3Client | HeadObject, GetObject, DeleteObject và các thao tác quản lý object |
| PostgreSQL | Store file metadata và business relationships |
| IAM Role | Grant Backend access to S3 |
| SQS (Future) | Asynchronous post-upload processing |

**Design principle:**

Backend không nên trực tiếp xử lý việc truyền dữ liệu file nếu không thực sự cần thiết. Backend tập trung vào nghiệp vụ, quyền truy cập và metadata; S3 chịu trách nhiệm lưu trữ file.

Với kiến trúc này, Moldy Market có thể tái sử dụng một cơ chế upload thống nhất cho nhiều module mà không phải triển khai logic S3 riêng lẻ.