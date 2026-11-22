# Danh mục Khiếm khuyết & Rủi ro Phát hiện (FINDINGS)

Báo cáo phân tích chất lượng cho hệ thống **Moldy-Market** (Backend Spring Boot, Module Lambda, Frontend User & Admin).

Quy ước mức độ nghiêm trọng:
- **P0**: Nghiêm trọng - Rủi ro thất thoát tài chính, bảo mật đặc quyền hoặc sai lệch tính toàn vẹn dữ liệu lõi. Cần xử lý trước khi kích hoạt luồng tiền hoặc release.
- **P1**: Cao - Lỗi logic nghiệp vụ, sai sót cấu hình cơ sở hạ tầng, hoặc rào cản ảnh hưởng chức năng chính.
- **P2**: Trung bình - Thiếu sót kiểm thử, thiếu bao phủ edge case, hoặc cấu hình chưa tối ưu.

---

## QA-001 · Wallet, Withdrawal và Escrow thiếu kiểm soát quyền sở hữu (IDOR)

- **Mức độ**: `P0`
- **Vị trí**: `com.moldy.moldymarket.wallet.controller.WalletController`, `WithdrawalController`, `EscrowController`; `wallet.service.WithdrawalService`, `EscrowService`.
- **Mô tả**:
  - `requestWithdrawal` chấp nhận `request.userId()` do caller gửi lên mà không xác thực rằng `userId` đó phải chính là người dùng đang đăng nhập trong Security Context.
  - `EscrowService.doHold` nhận ID của buyer/seller trực tiếp từ DTO request mà không kiểm tra chéo quyền hạn người gọi.
  - Endpoint xem số dư ví `/api/v1/wallets/{userId}` cho phép truyền bất kỳ UUID nào để tra cứu thông tin số dư.
- **Cách tái hiện**:
  1. Đăng nhập với tài khoản User A (nhận Token A).
  2. Gửi request `POST /api/v1/withdrawals` với header `Authorization: Bearer <Token A>`, nhưng trong JSON body đặt `userId: "<UUID của User B>"`.
  3. Hệ thống tiến hành trừ tiền ví của User B thay vì User A.
- **Hành vi mong đợi**: Trả về `403 Forbidden` hoặc chỉ lấy `userId` trực tiếp từ `Authentication.getPrincipal()` / `@CurrentUser`.
- **Kế hoạch kiểm thử**: Viết test hành vi mong đợi trong Phase 3 & Phase 4 (đánh dấu `@Disabled("QA-001 IDOR wallet")` cho đến khi production logic được patch).

---

## QA-002 · Hoàn tiền rút ví sai khóa và rủi ro trạng thái đồng thời

- **Mức độ**: `P0`
- **Vị trí**: `com.moldy.moldymarket.wallet.service.WithdrawalService.java` (hàm `reverseHold`).
- **Mô tả**:
  - Trong phương thức `reverseHold`, code gọi:
    ```java
    Wallet wallet = walletRepository.findByIdForUpdate(withdrawal.getId())
    ```
    thay vì `withdrawal.getWalletId()`. `withdrawal.getId()` là ID của bản ghi rút tiền (`Withdrawal`), hoàn toàn khác với ID của bản ghi Ví (`Wallet`). Lỗi này dẫn đến việc không tìm thấy ví hoặc cập nhật nhầm sang một ví khác nếu ID trùng lặp.
  - Phương thức `fail(...)` thiếu `@Transactional`, có nguy cơ cập nhật trạng thái dở dang nếu xảy ra exception giữa chừng.
- **Cách tái hiện**:
  1. Tạo một yêu cầu rút tiền mới có `Withdrawal.id = UUID_W` và `Wallet.id = UUID_V` (với `UUID_W != UUID_V`).
  2. Gọi `rejectWithdrawal(...)` để từ chối yêu cầu và kích hoạt hoàn tiền `reverseHold`.
  3. Hệ thống ném ngoại lệ không tìm thấy ví, tiền đóng băng không được hoàn trả về số dư khả dụng.
- **Hành vi mong đợi**: Phải truy vấn ví bằng `withdrawal.getWalletId()` và bọc toàn bộ thao tác trong giao dịch `@Transactional`.
- **Kế hoạch kiểm thử**: Viết Unit Test và Persistence Test thể hiện đúng hành vi mong đợi trong Phase 1 (Module Wallet) và Phase 2.

---

## QA-003 · Khai báo DDL index của Payment sai tên cột mapping

- **Mức độ**: `P1`
- **Vị trí**: `com.moldy.moldymarket.payment.entity.Payment.java`.
- **Mô tả**:
  - Entity `Payment` khai báo index Hibernate:
    ```java
    @Index(name = "idx_payments_status_expires", columnList = "status, expires_at")
    ```
    tuy nhiên thuộc tính trong Entity được map là `@Column(name = "expired_at") private Instant expiredAt;`.
  - Cột trong bảng là `expired_at`, không có cột `expires_at`.
- **Cách tái hiện**:
  - Khởi động ứng dụng Spring Boot kết nối vào database PostgreSQL sạch với `spring.jpa.hibernate.ddl-auto=update`.
  - Hibernate log ra lỗi ngoại lệ SQL: `column "expires_at" does not exist` khi cố gắng thực thi câu lệnh `CREATE INDEX`.
- **Hành vi mong đợi**: Cột trong `columnList` phải sửa thành `expired_at`.
- **Kế hoạch kiểm thử**: Kiểm tra trong Phase 2 (Persistence test trên PostgreSQL 16 Testcontainers).

---

## QA-004 · CORS cấu hình thiếu header tùy chỉnh và cờ credentials

- **Mức độ**: `P1`
- **Vị trí**: `com.moldy.moldymarket.config.SecurityConfig.java`.
- **Mô tả**:
  - Frontend gọi API xác thực có kèm header đặc thù `X-Moldy-Auth` và gửi kèm refresh token qua cookie (`credentials: 'include'`).
  - Trong cấu hình `SecurityConfig`, danh sách allowed headers chưa khai báo `X-Moldy-Auth` và chưa bật `setAllowCredentials(true)` một cách chặt chẽ theo origin tin cậy.
- **Cách tái hiện**:
  - Chạy Frontend trên port riêng (ví dụ `http://localhost:5173`) gọi sang Backend `http://localhost:8080/api/v1/auth/login`.
  - Trình duyệt chặn request preflight (OPTIONS) do vi phạm chính sách CORS (thiếu allowed header và credentials).
- **Hành vi mong đợi**: Preflight trả về `Access-Control-Allow-Origin: http://localhost:5173`, `Access-Control-Allow-Credentials: true`, `Access-Control-Allow-Headers: ..., X-Moldy-Auth`.
- **Kế hoạch kiểm thử**: Viết test CORS preflight trong Phase 3 & Phase 4.

---

## QA-005 · Spring Boot context test phụ thuộc môi trường ngoài (Database & Config)

- **Mức độ**: `P1`
- **Vị trí**: `src/test/java/com/moldy/moldymarket/MoldyMarketApplicationTests.java`.
- **Mô tả**:
  - Test class `@SpringBootTest` hiện tại kết nối trực tiếp vào thông số `spring.datasource.*` mặc định trong `application.properties` trỏ tới `localhost:5432/moldy_market`.
  - Khi chạy `mvn test` trên môi trường CI sạch hoặc máy dev chưa bật DB với đúng credentials mặc định, test sẽ fail ngay lập tức với lỗi `FATAL: password authentication failed for user "postgres"`.
- **Cách tái hiện**:
  - Đổi mật khẩu postgres hoặc tắt dịch vụ local, chạy `./mvnw test`. Toàn bộ build bị crash.
- **Hành vi mong đợi**: Bộ test phải hoàn toàn độc lập, sử dụng profile `test` và Testcontainers để tự cấp container PostgreSQL cô lập cho context test.
- **Kế hoạch kiểm thử**: Chuyển đổi harness sang Testcontainers trong Phase 2 & Phase 3.

---

## QA-006 · Module Notification và tích hợp Chat của Appraisal còn ở dạng Stub

- **Mức độ**: `P2`
- **Vị trí**: `com.moldy.moldymarket.notification.service.NotificationService.java`, `appraisal.assignment.service.AppraisalConversationService.java`.
- **Mô tả**:
  - `NotificationService` hiện tại trả về dữ liệu rỗng / mock tĩnh; các phương thức đánh dấu đã đọc (`markAsRead`), xóa thông báo chỉ có phần thân rỗng.
  - `AppraisalConversationService` ghi chú "TODO: Integrate with Chat API".
- **Hành vi mong đợi**: Cần hoàn thiện tích hợp khi core chat và notification queue đi vào hoạt động chính thức.
- **Kế hoạch kiểm thử**: Viết mock test cho các interface hiện tại trong Phase 1; đánh dấu rõ phạm vi stub.

---

## QA-007 · Cấu hình secrets fallback trong application.properties

- **Mức độ**: `P1`
- **Vị trí**: `src/main/resources/application.properties`.
- **Mô tả**:
  - Một số cấu hình quan trọng có fallback mặc định (như `security.jwt.secret`, `admin.password=<ADMIN_PASSWORD_REDACTED>`, `spring.datasource.password=123456`).
  - Nếu triển khai lên production mà quên set biến môi trường, hệ thống sẽ sử dụng các giá trị secret mặc định công khai.
- **Hành vi mong đợi**: Bắt buộc yêu cầu biến môi trường khi chạy ở profile `prod` / production.
- **Kế hoạch kiểm thử**: Xác minh profile isolation trong Phase 4 và Phase 7.

---

## QA-008 · Admin Frontend CI có thể bị bỏ qua do cấu hình passWithNoTests

- **Mức độ**: `P2`
- **Vị trí**: `C:\Do An Tot Nghiep - frontend\Moldy-Market-Admin-Frontend\vite.config.js`.
- **Mô tả**:
  - Dự án Admin Frontend hiện có cấu hình `passWithNoTests: true` và hiện tại chưa có file test nào được viết (`0 test files`).
  - Lệnh `npm test` trả về exit code 0 ("No test files found, exiting with code 0"), tạo cảm giác an toàn giả trên CI pipeline.
- **Hành vi mong đợi**: Cần bổ sung bộ test cho Admin Frontend ở Phase 5 và gỡ bỏ cấu hình `passWithNoTests`.
- **Kế hoạch kiểm thử**: Triển khai bộ test toàn diện cho Admin Frontend ở Phase 5.

---

## QA-009 · Đồng bộ version dependency khi cài đặt coverage provider cho Frontend

- **Mức độ**: `P2`
- **Vị trí**: `package.json`, `package-lock.json` của 2 Frontend apps.
- **Mô tả**:
  - Khi cài đặt `@vitest/coverage-v8`, nếu chạy `npm install` không khớp phiên bản với `vitest` (hiện đang dùng `^5.1.0`), có thể dẫn đến việc nâng version ngoài ý muốn của các thư viện liên quan như `react-router` hoặc `@testing-library`, gây lỗi test môi trường jsdom.
- **Hành vi mong đợi**: Phải khóa chặt phiên bản tương thích (`@vitest/coverage-v8@^5.1.0`) và tuân thủ `package-lock.json`.
- **Kế hoạch kiểm thử**: Thực hiện ở Phase 5 & Phase 7.

---

## QA-010 · AuthenticationService.register truyền user.getId() thay vì savedUser.getId() vào createWalletForUser

- **Mức độ**: `P1`
- **Vị trí**: `com.moldy.moldymarket.user.service.AuthenticationService.java` (dòng 139).
- **Mô tả**:
  - Trong phương thức `register`, sau khi lưu entity:
    ```java
    User savedUser = userRepository.save(user);
    // ...
    walletService.createWalletForUser(user.getId());
    ```
  - `user` là thực thể mới được ánh xạ từ `userMapper.toEntity(request)` với `id` bị ignore (giá trị `null`). Khi gọi `user.getId()`, giá trị truyền vào `walletService.createWalletForUser` là `null` (thay vì `savedUser.getId()` chứa UUID mới được sinh bởi JPA).
- **Cách tái hiện**:
  - Chạy unit test cô lập hoặc luồng đăng ký tài khoản với repository mock: `walletService.createWalletForUser(null)` được gọi, dẫn đến ví được tạo với `userId = null` hoặc ném lỗi vi phạm ràng buộc dữ liệu.
- **Hành vi mong đợi**: Phải gọi `walletService.createWalletForUser(savedUser.getId())`.
- **Kế hoạch kiểm thử**: Đánh dấu `@Disabled("QA-010")` cho test case mong đợi `createWalletForUser(savedUser.getId())` trong `AuthenticationServiceTest` cho đến khi production code được sửa.
