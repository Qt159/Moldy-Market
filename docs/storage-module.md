# Storage và upload media

> **Trạng thái tài liệu:** mô tả contract theo mã nguồn hiện tại của Moldy Market. Phần liên kết media cho Product, Appraisal, Dispute và avatar đã được bổ sung trong backend; không đồng nghĩa với việc môi trường staging/S3 thật đã được kiểm thử. Xem [Kiểm thử và trạng thái nghiệm thu](#kiểm-thử-và-trạng-thái-nghiệm-thu).

## 1. Mục tiêu và nguyên tắc

Storage quản lý metadata trong PostgreSQL, còn bytes nằm trong bucket S3 private. Backend cấp presigned PUT để trình duyệt tải trực tiếp lên S3; sau confirm backend kiểm tra object trước khi đánh dấu khả dụng. Các module nghiệp vụ lưu `StoredFile.id`, không lưu URL có chữ ký làm dữ liệu lâu dài.

Các nguyên tắc:

- Frontend không nhận AWS credentials và không tự chọn `objectKey`.
- `uploadUrl` chỉ dùng cho thao tác PUT trong thời hạn ngắn; không dùng làm URL hiển thị.
- URL xem media là URL presigned GET tạm thời, được tạo khi API nghiệp vụ trả media sau kiểm tra quyền.
- Không dựng URL public từ tên bucket hoặc `objectKey`.
- Thay đổi hiện tại không thay purpose, giới hạn hay luồng upload của các purpose khác ngoài các ràng buộc đã có trong Storage Core.

## 2. Luồng upload

1. Người dùng đã đăng nhập gọi `POST /api/v1/files/presigned-url` với tên file, MIME, kích thước và purpose.
2. Backend kiểm tra định dạng được phép theo purpose, phần mở rộng, MIME và giới hạn kích thước; tạo object key ngẫu nhiên và bản ghi `StoredFile` trạng thái `PENDING`.
3. Backend ký presigned PUT với `Content-Type` mong đợi và trả `fileId`, `uploadUrl`, `objectKey`, `expiresIn`.
4. Frontend PUT trực tiếp bytes lên `uploadUrl`, gửi đúng `Content-Type` đã dùng khi xin URL.
5. Frontend gọi `POST /api/v1/files/{fileId}/confirm`.
6. Backend yêu cầu file thuộc người gọi, kiểm tra object qua S3 `HeadObject`, so kích thước/MIME với metadata đã lưu, rồi đọc nội dung để xác minh chữ ký/định dạng thực tế.
7. Nếu hợp lệ, file chuyển thành `AVAILABLE`; nếu metadata hoặc nội dung không hợp lệ, file bị từ chối và được đưa vào quy trình dọn dẹp. Sau đó frontend gửi `fileId` đến API module nghiệp vụ phù hợp để liên kết.
8. Khi đọc Product/Appraisal/Dispute/avatar, backend kiểm tra quyền ở lớp nghiệp vụ trước khi trả URL xem.

### API upload

`POST /api/v1/files/presigned-url` (cần đăng nhập)

Request:

```json
{
  "filename": "product-image.jpg",
  "contentType": "image/jpeg",
  "fileSize": 234567,
  "purpose": "PRODUCT_IMAGE"
}
```

Response `201`:

```json
{
  "fileId": "f4d1f5cd-1f0a-45ce-8ccb-7399b74884bd",
  "uploadUrl": "https://...",
  "objectKey": "PRODUCT_IMAGE/<owner>/<random>.jpg",
  "expiresIn": 300
}
```

`objectKey` có trong contract hiện tại để tương thích với API cũ; frontend không được tự tạo hoặc sửa key. Không lưu `uploadUrl` hay `objectKey` vào hồ sơ nghiệp vụ.

`POST /api/v1/files/{fileId}/confirm` (chủ sở hữu đã đăng nhập) trả metadata file, gồm `fileId`, `originalFilename`, `contentType`, `fileSize`, `purpose`, `status`, `uploadedAt`. Confirm không tự liên kết file vào Product, Appraisal, Dispute hay hồ sơ người dùng.

### Loại file và giới hạn

| Nhóm | Định dạng được nhận | Giới hạn |
|---|---|---:|
| Ảnh | JPEG/JPG, PNG, WEBP | 5 MiB |
| Video | MP4, WEBM | 50 MiB |
| Tài liệu PDF hiện có | PDF, chỉ các purpose có hỗ trợ tài liệu | 20 MiB |

Purpose ảnh/video hiện gồm `PRODUCT_IMAGE`, `DISPUTE_EVIDENCE`, `APPRAISAL_EVIDENCE`, `APPRAISAL_REPORT`; `USER_AVATAR` chỉ nhận ảnh. `ORDER_DOCUMENT` tiếp tục là PDF. Chi tiết chính sách purpose nằm trong `S3FileStorageService`; không suy ra rằng mọi purpose nhận mọi định dạng.

Confirm kiểm tra MIME/size tại S3 và nội dung thực tế: ảnh phải qua kiểm tra signature và decoder (có giới hạn pixel), MP4/WebM kiểm tra magic bytes, PDF được parse bằng PDFBox. Đây là xác thực định dạng cơ bản, không phải antivirus hoặc phân tích codec đầy đủ.

## 3. Liên kết file trong module nghiệp vụ

Tất cả thao tác liên kết chỉ nhận ID file đã upload và confirm. Backend kiểm tra tồn tại, chủ sở hữu là user yêu cầu, purpose đúng, trạng thái `AVAILABLE`, và file chưa được liên kết. Những bước này nằm trong transaction nghiệp vụ cùng với thao tác lưu liên kết. File ID trùng hoặc đã liên kết bị từ chối.

### Product

- `POST /api/v1/products`: có thể gửi `imageFileIds` (tối đa 10) để tạo danh sách media mới.
- API cập nhật Product hiện có: có thể gửi `imageFileIds` để thay toàn bộ danh sách file media; gửi `[]` để gỡ danh sách này.
- Dùng `PRODUCT_IMAGE`; owner là seller đang đăng nhập. Việc cập nhật vẫn qua kiểm tra quyền sở hữu/store và điều kiện chỉnh sửa Product hiện hành.
- Trường `images` dạng URL cũ vẫn được duy trì. Trong một request không gửi đồng thời URL legacy và `imageFileIds`. Khi dùng file ID, URL legacy được xóa khỏi Product; khi thay lại bằng URL legacy thì các StoredFile đang gắn bị unlink.
- Chi tiết và danh sách Product trả media sau khi query đã áp dụng quyền/visibility của endpoint. Product public có thể trả media cho người xem public; endpoint quản lý seller giữ guard hiện hành.

Ví dụ request tạo/cập nhật:

```json
{
  "imageFileIds": [
    "f4d1f5cd-1f0a-45ce-8ccb-7399b74884bd",
    "5d61b15e-d835-4a84-aa56-619161a9848a"
  ]
}
```

### Appraisal

- Khi nộp appraisal report, gửi `mediaFileIds` (tối đa 10) thay cho URL ảnh/tài liệu legacy.
- File phải thuộc appraiser được phân công, `AVAILABLE`, purpose `APPRAISAL_EVIDENCE`.
- Người bán của appraisal và appraiser được phân công có thể đọc report/media theo quyền nghiệp vụ hiện tại. Người ngoài bị từ chối.
- Workflow nộp, duyệt, từ chối và revision không thay đổi. Các trường URL cũ vẫn được giữ để tương thích; không gửi URL legacy và `mediaFileIds` cùng request.

### Dispute

- Khi tạo dispute, gửi `imageFileIds` (tối đa 5) thay cho `imageUrls` legacy.
- File phải thuộc người tạo dispute (buyer đang đăng nhập), `AVAILABLE`, purpose `DISPUTE_EVIDENCE`.
- Buyer và seller tham gia tranh chấp, cùng admin ở endpoint review/detail được bảo vệ, có thể đọc media qua các API tương ứng. Người ngoài bị từ chối.
- Tạo lại dispute đã hủy sẽ bỏ liên kết media cũ trước khi gắn danh sách mới; workflow dispute giữ nguyên. URL legacy tiếp tục được hỗ trợ, không trộn URL và ID trong một request.

### Avatar người dùng và appraiser

- Sau confirm file `USER_AVATAR`, cập nhật `PUT /api/v1/users/me` bằng `avatarFileId`; profile đọc lại có `avatarUrl` ổn định dạng `/api/v1/files/{fileId}/view`.
- Endpoint view avatar kiểm tra file khả dụng và purpose avatar rồi redirect sang presigned GET ngắn hạn. Bucket vẫn private.
- Appraiser register/update cũng hỗ trợ `avatarFileId`; URL avatar cũ vẫn được hỗ trợ. Legacy SigV4 URL không được lưu/đưa ra làm avatar URL.
- Với user profile, request `avatarUrl: ""` dùng để gỡ avatar; thay bằng `avatarFileId` hoặc URL legacy hợp lệ sẽ thay liên kết trước đó. Khi có cả hai, file ID có ưu tiên theo service hiện tại.

## 4. Response media và URL xem

Các response domain có thêm `imageFileIds` hoặc `mediaFileIds` cùng mảng `media`. Mỗi phần tử có dạng:

```json
{
  "id": "f4d1f5cd-1f0a-45ce-8ccb-7399b74884bd",
  "mediaType": "IMAGE",
  "contentType": "image/jpeg",
  "fileName": "product-image.jpg",
  "viewUrl": "https://...presigned-get...",
  "expiresIn": 300
}
```

Video trả `mediaType: "VIDEO"`; PDF trả `DOCUMENT` ở các luồng tài liệu có hỗ trợ. `viewUrl` có hạn dùng cấu hình được và **không lưu trong database**. Frontend hiển thị ảnh/video từ URL đó; khi hết hạn, gọi lại endpoint đọc Product/report/dispute để lấy URL mới. Video được trả inline với content type gốc để trình duyệt phát trực tiếp; không có transcoding.

Chỉ các API nghiệp vụ đã kiểm tra quyền mới dùng `getMediaViews`. `GET /api/v1/files/{fileId}/download-url` hiện chỉ dành cho owner và file `AVAILABLE`, không phải endpoint chia sẻ media cho buyer/seller/appraiser. `GET /api/v1/files/{fileId}/view` là đường view ổn định cho avatar; không dùng endpoint này thay cho quyền truy cập media domain.

## 5. Schema và migration

`StoredFile` giữ metadata, `status`, `purpose`, `ownerId`, `objectKey` và `linkedAt`. Quan hệ nghiệp vụ dùng các bảng nối riêng có thứ tự:

| Bảng | Parent | File |
|---|---|---|
| `product_media_files` | `products` | `stored_files` |
| `appraisal_report_media_files` | `appraisal_reports` | `stored_files` |
| `dispute_media_files` | `dispute_requests` | `stored_files` |

Entity sử dụng ordered `@ElementCollection`; bảng nối có FK, thứ tự media và unique `file_id` trong từng loại liên kết. Migration [V20261126__business_stored_file_links.sql](../src/main/resources/db/migration/V20261126__business_stored_file_links.sql) chỉ tạo bảng mới, không xóa hoặc chuyển dữ liệu URL cũ. Avatar dùng cột `users.avatar_file_id` trong migration riêng.

Kho lưu trữ hiện không được quản lý hoàn toàn bằng Flyway/Liquibase cho mọi môi trường; migration liên kết domain này được đánh dấu manual trong dự án. Trước khi deploy, cần xem lịch sử schema của môi trường, backup, áp dụng migration trên staging và xác nhận tên bảng/kiểu UUID/FK tương thích. Không chạy migration production khi chưa đối chiếu schema thực tế.

## 6. Thay đổi so với contract URL cũ

| Trước | Hiện tại | Tác động |
|---|---|---|
| Product/Appraisal/Dispute lưu URL media trực tiếp | Có thể lưu StoredFile ID; URL legacy vẫn tồn tại | Có định danh file ổn định, không phụ thuộc URL có hạn |
| Không có liên kết file đủ để xác minh quyền domain | Bảng nối domain, kiểm tra owner/purpose/status khi liên kết | Có thể kiểm soát ai gắn file và ai nhận URL xem |
| Response dùng URL legacy hoặc metadata URL rời rạc | Có thêm `media[]` và URL xem tạm thời | Frontend phân biệt IMAGE/VIDEO và xin URL mới khi hết hạn |
| URL S3 có nguy cơ bị lưu lâu dài | URL GET được ký khi tạo response; không persist | Bucket private, giảm nguy cơ URL hết hạn trong DB |
| File không có trạng thái linked của module nghiệp vụ | `linkedAt` được set/clear khi liên kết/thay/gỡ | Cleanup có thể phân biệt file đang dùng với upload bỏ dở |

Các API cũ được giữ ở mức field legacy để giảm phá vỡ frontend. Chuyển sang ID nên làm theo từng module; frontend không nên tiếp tục lưu presigned URL trong các field URL cũ.

## 7. Lợi ích và đánh đổi

### Lợi ích

- File có một ID và metadata do backend quản lý; thay URL/CDN/storage sau này không đòi sửa dữ liệu nghiệp vụ.
- Kiểm tra owner, purpose, trạng thái và quyền parent trước khi cấp URL xem.
- Bucket có thể private; URL xem chỉ có thời hạn và được tạo đúng lúc đọc.
- Thay/gỡ media cập nhật liên kết thay vì phải so sánh URL và object key.
- Có thể dọn upload chưa confirm/không còn liên kết bằng trạng thái lưu trữ hiện hành.
- Hỗ trợ IMAGE và VIDEO trong response domain, giữ các luồng PDF hiện có.

### Đánh đổi

- Cần một request liên kết sau bước confirm; upload thành công chưa có nghĩa media đã gắn vào Product/report/dispute.
- `viewUrl` hết hạn; frontend phải gọi lại API nghiệp vụ thay vì lưu URL làm cache dài hạn.
- Mỗi response có media có thể cần thao tác ký GET cho từng file, làm tăng độ trễ/tải khi danh sách dài.
- Migration thêm bảng quan hệ và phải được triển khai trước code dùng các bảng đó.
- URL legacy còn tồn tại để tương thích; hai kiểu dữ liệu sẽ cùng tồn tại cho tới khi frontend/dữ liệu cũ được chuyển đổi.
- `linkedAt` là cờ liên kết chung ở StoredFile, trong khi ID cũng nằm ở bảng domain; thao tác thay/gỡ phải đi qua service nghiệp vụ để giữ nhất quán.

## 8. Xóa file và dọn dẹp

API `DELETE /api/v1/files/{fileId}` chỉ dành cho owner theo chính sách hiện tại. Service đưa file sang trạng thái không khả dụng trước khi xóa S3; job nền dọn upload `PENDING` quá hạn và retry các trường hợp xóa lỗi. Các response media chỉ bao gồm file `AVAILABLE` đang được đánh dấu linked.

Frontend nên gỡ/thay file qua API nghiệp vụ trước khi yêu cầu xóa file. Không dựa riêng vào URL cũ để tìm liên kết StoredFile. Upload chưa confirm có thể được dọn sau thời gian pending được cấu hình; confirm kiểm tra nội dung sau khi bytes đã lên S3 nên giới hạn kích thước được phát hiện chắc chắn ở confirm, không chặn hoàn toàn lượng bytes upload trước đó.

## 9. Triển khai S3

Backend dùng AWS SDK credential provider mặc định. Môi trường EC2/ECS nên cấp IAM role; không hardcode AWS access key trong frontend hoặc cấu hình ứng dụng. Bucket cần private, bật Block Public Access, TLS và encryption theo cấu hình hạ tầng. CORS chỉ cho phép origin frontend cần thiết, method `PUT` và header `Content-Type` (cộng header mã hóa nếu dùng).

Frontend phải gửi `Content-Type` khớp với giá trị đã ký. Các lỗi thường gặp:

- `AccessDenied`: IAM role, bucket policy hoặc KMS policy thiếu quyền.
- `NoSuchBucket`: sai bucket/region hoặc bucket chưa tồn tại.
- `SignatureDoesNotMatch`: header/Content-Type/region không khớp URL ký.
- `EntityTooLarge`: vượt giới hạn nghiệp vụ; presigned PUT có thể chỉ phát hiện ở confirm.
- Confirm báo file chưa upload: PUT thất bại, URL hết hạn hoặc key không đúng.

Code trong repository không chứng minh cấu hình CORS, bucket privacy, IAM role, TLS/encryption policy hay quyền truy cập staging. Các mục này cần được kiểm tra trong hạ tầng thật.

## 10. Frontend integration

Quy trình cho Product/Appraisal/Dispute/avatar:

1. Gọi presigned-url với MIME, extension, size và purpose đúng module.
2. PUT bytes lên `uploadUrl`, gửi đúng header `Content-Type`.
3. Gọi confirm và chỉ tiếp tục khi trạng thái trả về là `AVAILABLE`.
4. Gửi `fileId` trong `imageFileIds`, `mediaFileIds` hoặc `avatarFileId` ở API nghiệp vụ.
5. Đọc `media[]` hoặc `avatarUrl`; dùng `viewUrl` cho `<img>`/`<video controls playsInline preload="metadata">`.
6. Hiển thị loading/fallback khi tải; nếu URL hết hạn/403, gọi lại API nghiệp vụ lấy URL mới. Không retry URL cũ vô hạn.

Thứ tự `media[]` theo danh sách ID trong yêu cầu. API trả thiếu media nếu file bị xóa/không còn `AVAILABLE`; frontend cần fallback nếu item biến mất.

## 11. Giới hạn version hiện tại

- Chưa có transcoding, HLS/DASH, adaptive bitrate, thumbnail tự sinh, CloudFront hoặc media Lambda.
- MP4/WebM được lưu nguyên trạng và phát trực tiếp từ S3 qua presigned GET; khả năng phát phụ thuộc codec bên trong container và trình duyệt. Kiểm tra magic bytes không xác nhận codec hoặc khả năng phát đầy đủ.
- Chưa có antivirus scanning nâng cao, kiểm duyệt nội dung, stripping metadata ảnh, hoặc sanitization PDF.
- Presigned PUT không áp đặt giới hạn byte cứng trước upload; backend so size ở bước confirm.
- Không có integration test đã chứng minh quyền liên module với PostgreSQL thật và S3 thật trong báo cáo hiện tại.
- Độ trễ/chi phí khi ký nhiều GET URL trong response lớn chưa được benchmark.
- URL legacy vẫn được trả trong trường tương thích ở một số response; frontend cần ưu tiên `media[]` khi có.
- Việc xóa file owner-only chưa đồng nghĩa có endpoint gỡ StoredFile theo mọi quy trình nghiệp vụ; thay/gỡ liên kết cần thông qua cập nhật resource.

## 12. Kiểm thử và trạng thái nghiệm thu

### Test được bổ sung

- Unit test kiểm tra liên kết StoredFile hợp lệ và từ chối PENDING/DELETED, sai owner, sai purpose.
- Unit test service cho Product tạo liên kết và trả media; Dispute phân quyền participant/outsider; Appraisal phân quyền seller/appraiser/outsider.
- Các test này dùng mock repository/service; chúng kiểm tra logic ứng dụng, không mô phỏng transaction/FK thật hoặc S3 thật.

### Kết quả chạy trong lần cập nhật tài liệu

- `git diff --check`: không phát hiện whitespace error; Git có cảnh báo line ending trên một số file.
- Nhóm Maven test mục tiêu **không chạy đến assertion**. Forked `javac` gặp `AccessDeniedException` khi đóng/đọc `C:\Users\Acer\.m2\repository\io\netty\netty-codec-base\4.2.15.Final\netty-codec-base-4.2.15.Final.jar`, sau đó test compilation báo package main không resolve được. Không coi đây là test pass hay lỗi logic đã xác định.
- Chưa áp dụng migration trên PostgreSQL staging, chưa chạy test integration với S3 thật/LocalStack, chưa thực hiện upload rồi phát ảnh/video trên EC2/staging.

### Điều kiện nghiệm thu còn lại

1. Khắc phục quyền/đường dẫn Maven local repository hoặc javac toolchain để compile và chạy unit tests.
2. Áp dụng migration trên PostgreSQL staging, kiểm tra FK, thứ tự media, rollback và các bản ghi URL cũ.
3. Chạy integration test: upload-confirm-link-read theo từng module; thử PENDING, sai owner/purpose, không tồn tại, file bị xóa và người ngoài.
4. Kiểm tra S3 thật/staging: bucket private, CORS, presigned PUT/GET, MIME, kích thước, hết hạn URL, IAM AccessDenied và phát MP4/WebM bằng trình duyệt mục tiêu.
5. Chỉ sau các bước trên mới đánh dấu toàn bộ luồng Storage tích hợp là nghiệm thu production/staging.
