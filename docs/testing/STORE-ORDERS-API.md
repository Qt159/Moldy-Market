# Backend: danh sách đơn hàng gian hàng

Ngày 2026-11-21. Trạng thái: IMPLEMENTED_LOCAL sau khi Chu chép. Đã sửa typo :staus thành :status trong OrderRepository, cài hai file tests và chạy full Maven verify/package trên Backend chính: 283 tests, 282 pass, 1 auth QA-010 disabled có sẵn, không failure/error. Từ task này frontend do trợ lý trực tiếp code, chỉ Backend cần Chu chép. Tests do trợ lý chuẩn bị, Chu không phải viết.

## Contract đã chốt

- GET /api/v1/stores/{storeId}/orders?page=1&size=20&status=PENDING_CONFIRMED
- Cần access token. Owner của đúng store hoặc staff thuộc đúng store có ORDER_PROCESS được xem. Quyền global của một shop khác không thay thế kiểm tra membership. Store bị BLOCKED dùng chính sách STORE_BLOCKED của StoreAccessGuard hiện có.
- page từ 1, mặc định 1; size từ 1 đến 100, mặc định 20. Bỏ status để xem mọi trạng thái; status là OrderStatus hiện có, không nhận chuỗi lạ. API trả INVALID_REQUEST cho page/size/UUID/status sai; chặn cả offset vượt giới hạn JPA int. Sort cố định createdAt DESC rồi id DESC; không nhận sort tùy ý.
- Trả ApiResponse<PagedResponse<OrderResponse>>. PagedResponse gồm content, page, size, hasNext, totalElements. Store chưa có đơn hoặc page vượt trang cuối trả HTTP 200 với content rỗng, không dùng ORDER_LIST_NOT_FOUND của endpoint buyer cũ.
- Các order được lọc bằng Order.storeId. sellerId vẫn là người đăng sản phẩm, không phải mặc định chủ shop. Các đơn shop khác và đơn cá nhân cũ không lọt vào danh sách này.
- POST /api/v1/orders/seller/{orderId}/confirm giữ nguyên code đã có: chỉ owner, COD PENDING_CONFIRMED sang CONFIRMED. Staff có ORDER_PROCESS chỉ xem ở task này, không được xác nhận thay owner. CONFIRMED chưa có nghĩa COD đã thu tiền. Không đổi admin giao hàng/thu tiền hoặc flow hủy/refund.

Ví dụ data khi chưa có đơn:

```json
{
  "content": [],
  "page": 1,
  "size": 20,
  "hasNext": false,
  "totalElements": 0
}
```

OrderResponse giữ nguyên các field hiện có: id, orderCode, checkoutGroupId, buyerId, sellerId, status, storeId, totalAmount, người nhận/địa chỉ, các mốc thời gian và items. Không có paymentMethod/paymentStatus trong DTO này: frontend không suy ra đã thanh toán từ CONFIRMED.

## Chép đủ 5 file

Đường dẫn bên dưới tính từ root Backend C:/Do An Tot Nghiep. Với file mới, tạo đúng package và tên class. Với file đã có, thay nội dung bằng block tương ứng; không xóa những module khác.

### 1. src/main/java/com/moldy/moldymarket/order/repository/OrderRepository.java

Thay toàn bộ file, giữ nguyên hai method cũ. Query mới lọc theo storeId, không lọc sellerId nên owner thấy đơn của staff. Fetch checkoutGroup/orderItems để mapper không tải từng quan hệ riêng; countQuery không join items để tổng số đơn không bị nhân lên.

```java
package com.moldy.moldymarket.order.repository;

import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query("select o.checkoutGroup.id from Order o where o.id = :id")
    Optional<UUID> findCheckoutGroupIdByOrderId(@Param("id") UUID id);

    List<Order> findAllByBuyerId(@Param("buyerId") UUID buyerId);

    @Query(value = """
            select distinct o from Order o
            left join fetch o.checkoutGroup
            left join fetch o.orderItems
            where o.storeId = :storeId and (:status is null or o.status = :status)
            """, countQuery = """
            select count(o) from Order o
            where o.storeId = :storeId and (:status is null or o.status = :status)
            """)
    Page<Order> findStoreOrders(@Param("storeId") UUID storeId,
                                @Param("status") OrderStatus status,
                                Pageable pageable);

}
```

### 2. src/main/java/com/moldy/moldymarket/order/service/StoreOrderService.java

Tạo file mới. Kiểm tra phân trang, lấy user từ session, gọi StoreAccessGuard với ORDER_PROCESS trước khi query dữ liệu khách hàng. Owner được xem; staff phải thuộc đúng shop và có quyền đã cấp. Dùng OrderResponse và PagedResponse hiện có, không sửa DTO cũ.

```java
package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.OrderMapper;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreOrderService {
    private final OrderRepository orderRepository;
    private final CurrentUser currentUser;
    private final StoreAccessGuard storeAccessGuard;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getStoreOrders(UUID storeId, int page, int size, OrderStatus status) {
        if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        UUID userId = currentUser.findCurrentUser().getId();
        storeAccessGuard.requireStoreAccess(storeId, userId, PermissionConstants.ORDER_PROCESS);
        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var orders = orderRepository.findStoreOrders(storeId, status, pageable);
        var content = orders.stream().map(orderMapper::toOrderResponse).toList();
        return PagedResponse.of(orders, content, pageable);
    }
}
```

### 3. src/main/java/com/moldy/moldymarket/order/controller/StoreOrderController.java

Tạo file mới, không đặt vào StoreController hiện có. Endpoint cần đăng nhập; UUID, int và enum sai kiểu được GlobalExceptionHandler hiện có trả INVALID_REQUEST. page mặc định 1 và size 20.

```java
package com.moldy.moldymarket.order.controller;

import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.service.StoreOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/orders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class StoreOrderController {
    private final StoreOrderService storeOrderService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getStoreOrders(
            @PathVariable UUID storeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(ApiResponse.success(storeOrderService.getStoreOrders(storeId, page, size, status)));
    }
}
```

### 4. src/main/java/com/moldy/moldymarket/config/CustomAuthenticationEntryPoint.java

Thay toàn bộ file. Chu đã duyệt sửa lỗi serialize Instant. Import ObjectMapper của Jackson 3 và dùng bean Spring qua constructor; giữ nguyên status/code/message 401.

```java
package com.moldy.moldymarket.config;

import com.moldy.moldymarket.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Object> body = ApiResponse.failure(
                "UNAUTHENTICATED",
                "Missing or invalid access token",
                null
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
```

### 5. src/main/java/com/moldy/moldymarket/config/CustomAccessDeniedHandler.java

Thay toàn bộ file. Cùng cách inject mapper cho JSON 403; giữ nguyên ACCESS_DENIED và thông báo hiện tại.

```java
package com.moldy.moldymarket.config;

import com.moldy.moldymarket.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Object> body = ApiResponse.failure(
                "ACCESS_DENIED",
                "You do not have permission to access this resource",
                null
        );

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

}
```

## Sau khi chép

1. Báo “xong Backend” để trợ lý đối chiếu source, tự đưa tests đã chuẩn bị vào src/test và chạy kiểm tra trên repo chính.
2. Chưa gọi endpoint từ frontend cho đến khi Backend chính compile và tests pass. Không đưa endpoint vào service chỉ dựa trên guide.
3. Sau khi kiểm tra xong, restart Backend. Với tài khoản owner/staff đúng quyền, gọi endpoint và kiểm tra content chỉ thuộc store đó. Staff thiếu ORDER_PROCESS và người ngoài phải bị chặn.
4. Trợ lý sẽ trực tiếp nối trang /store/orders: phân trang, lọc trạng thái, loading/empty/error/forbidden và nút xác nhận COD dành cho owner. Chu không cần chép frontend nữa.

## Kiểm chứng và cách đọc tests

Kiểm chứng bản review ngày 2026-11-21: full Maven verify/package pass 283 tests: 282 pass, 1 auth QA-010 disabled từ trước, không failure/error. Có 19 test mới (8 unit service, 11 PostgreSQL/HTTP gồm JSON 401/403), không tạo test skip mới. Backend chính sau khi Chu chép cũng đã qua cùng full verify/package. Đã đọc review về correctness, readability, architecture, security và pagination; không đổi dependency.

Tests đã được đưa vào src/test/java/com/moldy/moldymarket/order/service/ sau khi Chu chép source. Bản giao ban đầu ngoài Maven source set vẫn được lưu để tham khảo:

- docs/testing/store-orders-tests/StoreOrderServiceTest.java: thứ tự kiểm tra quyền trước query, phân trang cố định, trang rỗng, bad page/size và offset overflow.
- docs/testing/store-orders-tests/StoreOrderHttpIntegrationTest.java: PostgreSQL, JWT và filter chain thật qua MockMvc; owner/staff, cách ly shop, anonymous, bad params, empty/filter/pagination với nhiều item, chính sách store BLOCKED, owner xác nhận đơn staff đăng, JSON 401/403.

Fixtures integration dùng user/store/product/order riêng và transaction tự rollback, không hủy hoặc xác nhận đơn thực của Chu. Test cần cùng datasource/JWT runtime từ IntelliJ; không đưa secret vào source hoặc log. Full verify còn có một auth QA-010 disabled từ trước, không tạo skip mới.

## Vì sao dùng fetch join ở đây

Bản project đã xác minh đang dùng Hibernate 7.4.1.Final trên PostgreSQL. Hibernate 7.4 hỗ trợ collection fetch join cùng pagination bằng xử lý giới hạn trong SQL; không áp dụng kết luận của các bản cũ rằng luôn giới hạn trong bộ nhớ. Integration test bật fail_on_pagination_over_collection_fetch và kiểm tra hai trang có nhiều order item. Nếu hạ Hibernate hoặc đổi DB, phải chạy lại tests này.

Nguồn đối chiếu: [Spring Data: Page và pagination](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html), [Hibernate 7.4: Limits and Fetch Joins](https://docs.hibernate.org/orm/current/whats-new/).

## Giới hạn

Code đã được áp dụng vào Backend chính và frontend đã nối trực tiếp; Backend đã kiểm thử PostgreSQL/JWT/MockMvc. Chưa kiểm thử HTTP từ giao diện thật bởi Chu. Không sửa entity, schema, SecurityConfig hoặc OrderService hiện tại. Chưa có UI/admin thu tiền COD, refund/dispute hay SePay Live trong task này. Code mới chỉ là phần API danh sách và sửa serialize JSON bảo mật đã được Chu duyệt. Không commit/push.
