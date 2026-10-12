# MoldyMarket — Báo cáo Tổng kết Tiến độ & Quy chuẩn Thiết kế Hệ thống

> **Tài liệu tham khảo đồ án / Kiến trúc kỹ thuật backend**  
> **Dự án**: MoldyMarket Backend (Spring Boot 4.1 / Spring Security 7.x / Java 23 / PostgreSQL)  
> **Cập nhật đến**: Hoàn thành Phân quyền Store & Store Staff  

---

## PHẦN 1. TỔNG HỢP NHỮNG GÌ ĐÃ HOÀN THÀNH

Từ đầu dự án đến thời điểm hiện tại, hệ thống đã hoàn thành 4 cột mốc nền tảng quan trọng nhất:

### 1.1. Security Audit & Cải tiến Authentication (SEC-001 → SEC-007)
- **SEC-001**: Thay JWT secret bằng chuỗi CSPRNG 512-bit trong `.env`.
- **SEC-002**: Loại bỏ hoàn toàn `System.out.println` làm lộ raw Refresh Token trong log.
- **SEC-003**: Loại bỏ truy vấn lặp trong `AuthenticationService.login()`.
- **SEC-004**: Xóa `updatable = false` trên trường `User.updatedAt` để Hibernate cập nhật thời gian sửa đổi đúng.
- **SEC-005**: Tối ưu `JwtAuthenticationFilter`, không gọi `isTokenValid()` hai lần.
- **SEC-006**: Cấu hình TTL refresh token từ file cấu hình, sửa tên method repository, chuẩn hoá UTF-8.
- **SEC-007**: Xử lý lỗi đăng nhập chuẩn xác: `DisabledException` (403), `LockedException` (403), `BadCredentialsException` (401).

### 1.2. Role System & Hệ thống khởi tạo tự động (Seeders)
- Định nghĩa 5 Role chuẩn: `USER`, `APPRAISER`, `STAFF`, `ADMIN`, `OWNER`.
- `RoleInitializer`: Chạy `@Order(1)` lúc khởi động app, tự động seed 5 roles vào bảng `roles` và khởi tạo tài khoản **System Admin** mặc định từ biến môi trường (`.env`).
- Luồng đăng ký: Tự động gán role `USER` cho tài khoản mới trong cùng transaction.

### 1.3. User Profile & Quản trị User (User Module)
- `GET /api/v1/users/me`: Xem hồ sơ cá nhân.
- `PUT /api/v1/users/me`: Cập nhật họ tên, số điện thoại (kiểm tra trùng SĐT).
- `PUT /api/v1/users/me/password`: Đổi mật khẩu (xác thực mật khẩu cũ, cấm trùng mật khẩu mới).
- `GET /api/v1/users`: Quản trị viên xem danh sách người dùng với phân trang `Slice` (không dùng Count query, tối ưu performance).
- `GET /api/v1/users/{id}`: Xem chi tiết thông tin tài khoản theo ID.
- `PUT /api/v1/users/{id}/status`: Admin khóa / mở khóa tài khoản (`ACTIVE`, `LOCKED`, `SUSPENDED`).

### 1.4. Phân quyền chi tiết RBAC (Role-Based Access Control)
- **Mô hình 3 loại quyền (GrantType)**: `FULL` (mặc định theo role), `CONDITIONAL` (kèm điều kiện nghiệp vụ), `DELEGATED` (ủy quyền động).
- `PermissionConstants`: Hằng số String chuẩn hóa hơn 30 mã quyền từ bảng thiết kế `MoldyMarket_Permission_Table.md`.
- `PermissionInitializer`: Chạy `@Order(2)` tự động seed toàn bộ quyền và ánh xạ Role ↔ Permission vào bảng `role_permissions`.
- `CustomUserDetailsService`: Tự động nạp đồng thời cả Role (`ROLE_...`) lẫn Permission vào danh sách `GrantedAuthority`.
- `GlobalExceptionHandler`: Bắt `AccessDeniedException` trả về đúng mã **HTTP 403 FORBIDDEN** (thay vì 500), token sai trả về **HTTP 401 UNAUTHORIZED**.

### 1.5. Store Module (Quản lý Gian hàng — Phase 1)
- `POST /api/v1/stores`: Đăng ký mở gian hàng (mỗi user tối đa 1 shop, tên shop độc nhất).
  - **Tự động cấp role `OWNER` kèm `storeId`** vào bảng `user_roles`.
- `GET /api/v1/stores/my-store`: Chủ shop xem thông tin gian hàng của mình.
- `PUT /api/v1/stores/my-store`: Chủ shop cập nhật tên, mô tả, địa chỉ lấy hàng (chặn khi shop bị khóa).
- `GET /api/v1/stores/{id}`: Public API xem thông tin gian hàng (cho người mua, không cần token).
- `GET /api/v1/stores`: Admin xem danh sách tất cả gian hàng (phân trang Slice).
- `PUT /api/v1/stores/{id}/status`: Admin khóa / mở khóa gian hàng (`ACTIVE`, `BLOCKED`).

### 1.6. Store Staff Module (Quản lý Nhân viên Gian hàng — Phase 2)
- Bảng `store_staff`: Lưu quan hệ Store ↔ User nhân viên kèm danh sách quyền được ủy quyền (`permissions` dạng `Set<String>`).
- `POST /api/v1/stores/my-store/staff`: Owner thêm nhân viên bằng Email + cấp các nhóm quyền ban đầu:
  - Chặn không cho Owner tự thêm chính mình.
  - Chặn thêm trùng nhân viên vào cùng 1 shop.
  - Whitelist quyền hợp lệ cho Staff (`PRODUCT_CREATE`, `APPRAISAL_REQUEST_CREATE`, `ORDER_PROCESS`, `ORDER_CANCEL`, `OFFER_HANDLE`, `CHAT_SEND`).
  - Luôn tự động kèm quyền `STORE_WORKSPACE_ACCESS`.
  - Đồng bộ cấp role `STAFF` kèm `storeId` trong `user_roles`.
- `GET /api/v1/stores/my-store/staff`: Owner xem danh sách nhân viên của shop mình.
- `PUT /api/v1/stores/my-store/staff/{staffId}/permissions`: Owner cập nhật / thay đổi quyền nhân viên.
- `DELETE /api/v1/stores/my-store/staff/{staffId}`: Owner sa thải nhân viên (xóa khỏi `store_staff` và xóa role `STAFF` trong `user_roles`).
- `GET /api/v1/stores/my-store/staff/me`: Nhân viên tự xem hồ sơ và các quyền được cấp của mình.
- **Nạp quyền ủy quyền động**: `CustomUserDetailsService` tự động đọc bảng `store_staff` để nạp các quyền ủy quyền vào Security Context mỗi khi nhân viên gửi request.

---

## PHẦN 2. QUY CHUẨN THIẾT KẾ THƯ MỤC & PHÂN TẦNG (ARCHITECTURE)

Dự án áp dụng mô hình **Package by Feature (Modular Monolith)** kết hợp phân tầng trách nhiệm rõ ràng (Layered Architecture):

```
com.moldy.moldymarket/
├── common/                     # Hạ tầng dùng chung toàn hệ thống
│   ├── exception/              # AppException, ErrorCode, GlobalExceptionHandler
│   └── response/               # ApiResponse, ApiError, PagedResponse
├── config/                     # Cấu hình hệ thống & khởi tạo startup
│   ├── SecurityConfig.java     # Cấu hình Spring Security, Filter chain, permitAll
│   ├── PaginationConfig.java   # Cấu hình phân trang 1-indexed, Pageable mặc định
│   ├── RoleInitializer.java   # Seed 5 roles và tài khoản System Admin
│   └── PermissionInitializer.java # Seed danh mục permissions và role_permissions
├── security/                   # Module bảo mật & danh tính
│   ├── jwt/                    # JwtService, JwtAuthenticationFilter, JwtProperties
│   ├── refreshToken/           # RefreshToken entity, repository, service
│   ├── user/                   # CustomUserDetails, CustomUserDetailsService
│   ├── annotation/             # Các annotation kiểm tra quyền (@IsAdmin, @IsOwner...)
│   ├── RoleConstants.java      # Hằng số tên Role (USER, OWNER, ADMIN...)
│   └── PermissionConstants.java# Hằng số mã quyền (PROFILE_MANAGE, ORDER_PAY...)
├── user/                       # Feature: Người dùng & Xác thực
│   ├── controller/             # AuthController, UserController
│   ├── dto/
│   │   ├── request/            # LoginRequest, RegisterRequest, UpdateProfileRequest...
│   │   └── response/           # AuthResponse, UserProfileResponse, UserSummaryResponse...
│   ├── entity/                 # User entity, UserStatus enum
│   ├── mapper/                 # UserMapper (MapStruct)
│   ├── repository/             # UserRepository
│   └── service/                # AuthenticationService, UserService
├── role/                       # Feature: Vai trò
│   ├── entity/                 # Role entity
│   └── repository/             # RoleRepository
├── userrole/                   # Feature: Quan hệ User ↔ Role (kèm storeId)
│   ├── entity/                 # UserRole entity
│   └── repository/             # UserRoleRepository
├── permission/                 # Feature: Quyền hạn chi tiết
│   ├── entity/                 # Permission, RolePermission, GrantType enum
│   └── repository/             # PermissionRepository, RolePermissionRepository
└── store/                      # Feature: Gian hàng & Nhân viên
    ├── controller/             # StoreController, StoreStaffController
    ├── dto/
    │   ├── request/            # CreateStoreRequest, UpdateStoreRequest, AddStaffRequest...
    │   └── response/           # StoreResponse, StaffResponse
    ├── entity/                 # Store, StoreStaff
    ├── enums/                  # StoreStatus enum
    ├── mapper/                 # StoreMapper, StoreStaffMapper (MapStruct)
    ├── repository/             # StoreRepository, StoreStaffRepository
    └── service/                # StoreService, StoreStaffService
```

### Nguyên tắc phân tầng:
1. **Controller**: Chỉ tiếp nhận HTTP request, parse tham số, validate bằng `@Valid`, phân quyền bằng `@PreAuthorize`, gọi Service và trả về `ResponseEntity<ApiResponse<T>>`. Tuyệt đối không viết logic nghiệp vụ hay query database ở Controller.
2. **Service**: Chứa 100% logic nghiệp vụ. Validate business rules, ném `AppException(ErrorCode.XYZ)`, gọi Repository, chuyển đổi sang DTO qua Mapper. Luôn có `@Transactional(readOnly = true)` trên class và `@Transactional` trên phương thức ghi/sửa/xóa.
3. **Repository**: Kế thừa `JpaRepository`, chỉ đảm nhiệm việc đọc/ghi DB. Sử dụng JPQL tối ưu khi cần join nhiều bảng hoặc fetch dữ liệu cụ thể để tránh lỗi N+1.
4. **Mapper**: Dùng MapStruct interface để sinh code ánh xạ compile-time, không dùng reflection (như ModelMapper) để đảm bảo tốc độ cao nhất.

---

## PHẦN 3. QUY TẮC THIẾT KẾ CÁC LỚP (DESIGN CONVENTIONS)

### 3.1. Request DTO — Luôn dùng Java `record`
Request DTO chỉ là túi dữ liệu trung chuyển, bất biến (immutable) và cần ngắn gọn:
```java
public record AddStaffRequest(
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be valid")
    String email,

    Set<String> permissions
) {}
```
- **Lý do**: Không cần boilerplate code (`getter`, `setter`, `equals`, `hashCode`, `toString`).
- Spring MVC tự động deserialize JSON vào record. Các annotation Bean Validation (`@NotBlank`, `@Size`, `@NotNull`) hoạt động hoàn hảo.
- Trong Service truy cập dữ liệu trực tiếp qua cú pháp `request.email()` (không có tiền tố `get`).

### 3.2. Response DTO & MapStruct
- Response DTO có thể là `record` hoặc `class`:
  - Nếu là `class`: Cần có `@Getter` và `@Setter` để MapStruct gán giá trị qua setter.
  - Nếu là `record`: MapStruct 1.6+ tự động map vào canonical constructor.
- Tách biệt Mapper thành Interface có `@Mapper(componentModel = "spring")`:
```java
@Mapper(componentModel = "spring")
public interface StoreStaffMapper {
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "storeId", source = "store.id")
    @Mapping(target = "email", source = "user.email")
    StaffResponse toResponse(StoreStaff staff);
}
```
- Khi thêm/sửa field trong DTO, nếu MapStruct sinh code cũ dẫn đến response bị `null`, giải pháp là **Rebuild Project** / `mvn clean compile` để MapStruct sinh lại file `*Impl.java`.

### 3.3. JPA Entity
- Trường ID dùng `UUID` với chiến lược sinh tự động:
  ```java
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  UUID id;
  ```
- Dùng `@FieldDefaults(level = AccessLevel.PRIVATE)` của Lombok để ngầm định mọi field là private, tránh gõ thừa `private`.
- Không lạm dụng `@Setter` bừa bãi trên các trường nhạy cảm như `id`, `ownerId`, `createdAt`.
- Quan hệ giữa các module độc lập: Ưu tiên lưu `UUID` (như `Store.ownerId`, `UserRole.storeId`) thay vì quan hệ `@ManyToOne` cứng nhắc nếu hai module không cần phụ thuộc chặt chẽ về vòng đời dữ liệu.

---

## PHẦN 4. CƠ CHẾ PHÂN QUYỀN RBAC (ROLE VS PERMISSION)

Đây là điểm sáng kiến trúc của hệ thống:

```
[User] ──(gán)──> [Role] ──(quy định)──> [Permission] ──(bảo vệ)──> [Endpoint]
  │                                           ▲
  └──(ủy quyền trực tiếp qua StoreStaff)───────┘
```

### 4.1. Sự khác biệt giữa Role và Permission:
- **Role (Vai trò)**: Đại diện cho "Người dùng là ai trong tổ chức" (`USER`, `APPRAISER`, `OWNER`, `STAFF`, `ADMIN`). Quản trị viên quản lý theo Role để không phải đi tick hàng chục quyền lẻ cho từng tài khoản.
- **Permission (Quyền hạn)**: Đại diện cho "Hành động cụ thể nào được phép thực thi" (`PRODUCT_CREATE`, `USER_STORE_MANAGE`, `ORDER_PAY`...). Endpoint gắn ổ khóa bằng Permission.

### 4.2. Cơ chế nạp quyền kết hợp (Hybrid Authorities Loading):
Khi một request gửi token lên server, `CustomUserDetailsService.buildUserDetails()` thực hiện 3 bước:
1. **Chuyển các Role thành Authority**: Tiền tố `ROLE_` (ví dụ `ROLE_USER`, `ROLE_OWNER`, `ROLE_STAFF`).
2. **Nạp các quyền cố định (FULL GrantType)**: Truy vấn 1 câu JPQL từ bảng `role_permissions` lấy toàn bộ mã quyền tương ứng với các role mà user nắm giữ.
3. **Nạp các quyền ủy quyền (DELEGATED)**: Nếu user là Store Staff, đọc thêm danh sách quyền riêng lẻ trong bảng `store_staff` nạp tiếp vào danh sách.

### 4.3. Cách bảo vệ Endpoint:
- Dùng `@PreAuthorize` kết hợp hằng số từ `PermissionConstants`:
```java
// Chỉ ai có quyền STORE_STAFF_MANAGE (mặc định là OWNER) mới được vào
@PreAuthorize("hasAuthority('" + PermissionConstants.STORE_STAFF_MANAGE + "')")
@PostMapping("/my-store/staff")
public ResponseEntity<?> addStaff(...) { ... }

// Cả OWNER (có sẵn trong role) và STAFF (được ủy quyền) đều dùng chung ổ khóa này:
@PreAuthorize("hasAuthority('" + PermissionConstants.PRODUCT_CREATE + "')")
@PostMapping("/products")
public ResponseEntity<?> createProduct(...) { ... }
```

---

## PHẦN 5. QUY CHUẨN XỬ LÝ LỖI & PHẢN HỒI (EXCEPTION HANDLING)

### 5.1. Định dạng Response chuẩn duy nhất: `ApiResponse<T>`
Mọi API thành công hay thất bại đều trả về cấu trúc thống nhất:
```json
{
    "success": true,
    "code": "SUCCESS",
    "message": "Request completed successfully",
    "data": { ... },
    "timestamp": "2026-10-12T09:00:00Z"
}
```

### 5.2. Quản lý mã lỗi tập trung: `ErrorCode`
Mọi lỗi nghiệp vụ đều được khai báo trong enum `ErrorCode` với 3 thông số: mã lỗi (`code`), thông điệp mặc định (`message`), và mã trạng thái HTTP (`httpStatus`):
```java
STORE_NOT_FOUND("STORE_001", "Store not found", HttpStatus.NOT_FOUND),
STORE_ALREADY_EXISTS("STORE_002", "You already have a store", HttpStatus.CONFLICT),
STAFF_NOT_FOUND("STORE_005", "Staff not found in this store", HttpStatus.NOT_FOUND),
```

### 5.3. Bắt lỗi tự động tại `GlobalExceptionHandler`:
- Bắt `AppException`: Rút `errorCode`, đóng gói vào `ApiResponse.failure()` và trả về đúng HTTP Status tương ứng.
- Bắt `MethodArgumentNotValidException`: Trích xuất danh sách lỗi validation theo từng field (`validationErrors`).
- Bắt `AccessDeniedException` (Spring Security): Trả về HTTP 403 `FORBIDDEN`.
- Bắt lỗi token (`CustomAuthenticationEntryPoint`): Trả về HTTP 401 `UNAUTHENTICATED`.
- Bắt lỗi không lường trước (`Exception.class`): Trả về HTTP 500 `INTERNAL_SERVER_ERROR` và ghi log ERROR.
