# Selective checkout integration — 2026-11-20

> Cập nhật: đây là snapshot lượt 1. Bản flow online/COD đã nối và code hiện tại nằm trong [CHECKOUT-PAYMENT-FLOW.md](CHECKOUT-PAYMENT-FLOW.md). Dùng bản mới khi đọc hoặc thử code.

Chu duyệt hợp nhất có chọn lọc từ feature/dispute (4a58390) lên nền main local (dc11b09). Branch làm việc: codex/selective-checkout-integration. Đây là lượt 1: giữ hàng và chạy hết hạn. Source đã được sửa trực tiếp; các block bên dưới giúp Chu đọc lại logic, không cần chép thêm lần nữa. Chưa commit hoặc push.

## Phần được chọn

Lấy ý tưởng khóa/giữ hàng khi tạo checkout và scheduler 60 giây từ nhánh mới. Giữ reservation riêng của local (reservedCheckoutId/reservationExpiresAt), StoreAccessGuard chặn owner/staff, quantity đúng 1, CartService và auth/profile/store hiện có. Không đưa source dispute/refund chưa compile vào bản này.

## 1. Tạo checkout: khóa trước, đọc sau, giữ trong cùng transaction

File: src/main/java/com/moldy/moldymarket/order/service/CheckoutGroupService.java. Khóa product theo ID đã sắp xếp để các buyer lấy khóa cùng thứ tự. Sau khóa mới kiểm tra status/reservation, giá và quyền mua. Save checkout cấp ID rồi giữ product bằng ID đó; lỗi tạo order/payment sẽ rollback cả reservation qua transaction hiện có. Online có hạn 15 phút tính từ cùng now; COD không có hạn tự động.

```java
@Transactional
    public CheckoutResponse createCheckoutGroup(CheckoutGroupRequest request){
        var user = currentUser.findCurrentUser(); //get current user
        Instant now = Instant.now();

        //map request item to order items
        //duplicate product and increase quantity
//        Map<UUID, Integer> requestedItems = request.orderItems()
//                .stream()
//                .collect(Collectors.toMap(
//                        OrderItemRequest::productId,
//                        OrderItemRequest::quantity,
//                        Integer::sum
//                ));
        Map<UUID, Integer> requestedItems = new HashMap<>();
        for(var item : request.orderItems()){
            if(item.productId() == null || item.quantity() == null || item.quantity() != 1)
                throw new AppException(ErrorCode.INVALID_ORDER_ITEMS);

            if(requestedItems.putIfAbsent(item.productId(), 1) != null){
                throw new AppException(ErrorCode.DUPLICATE_PRODUCT);
            }
        }


        List<Product> products = new ArrayList<>();
        // Lock in the same order for every buyer before reading status, price or reservation.
        for (UUID productId : requestedItems.keySet().stream().sorted().toList()) {
            products.add(productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND)));
        }

        //validate products
        for (var product : products) {
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new AppException(ErrorCode.PRODUCT_NOT_ACTIVE);
            }

            if (product.isReservedAt(now)) {
                throw new AppException(ErrorCode.PRODUCT_RESERVED);
            }

            if (product.getSellerId() == null) {
                throw new AppException(ErrorCode.INVALID_PRODUCT);
            }

            boolean isSeller = product.getSellerId().equals(user.getId());

            boolean isStoreMember = product.getStoreId() != null
                    && storeAccessGuard.isStoreMember(
                            product.getStoreId(),
                            user.getId()
            );

            if (isSeller || isStoreMember) {
                throw new AppException(ErrorCode.CHECKOUT_OWN_PRODUCT);
            }
        }

        Map<OrderGroupKey, List<Product>> groupedProducts = products.stream()
                .collect(Collectors.groupingBy(
                        product -> new OrderGroupKey(product.getSellerId(),
                                product.getStoreId())
                ));
        //expired at
        Instant expiredAt = null;
        if(request.method() == PaymentMethod.ONLINE_BANKING) expiredAt = now.plus(15, ChronoUnit.MINUTES);

        //create CheckoutGroup
        CheckoutGroup checkoutGroup = CheckoutGroup.builder()
                .buyerId(user.getId())
                .totalAmount(BigDecimal.ZERO)
                //.platformDiscount(request.platformVoucherId())
                .expiredAt(expiredAt)
                .build();

        checkoutGroupRepository.save(checkoutGroup);

        for (Product product : products) {
            product.reserveForCheckout(checkoutGroup.getId(), expiredAt, now);
        }

        //create orders
        List<Order> orders = new ArrayList<>();
        for(var entry : groupedProducts.entrySet()){
            Order order = createOrder(
                    checkoutGroup,
                    user.getId(),
                    entry.getKey(),
                    entry.getValue(),
                    requestedItems,
                    request
            );

            checkoutGroup.addOrder(order);
            orders.add(order);
        }

        //calculate checkout total from orders
        BigDecimal checkoutTotal = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        checkoutGroup.setTotalAmount(checkoutTotal);
        checkoutGroup.setOrders(orders);

        orderRepository.saveAll(orders);

        List<OrderSummaryResponse> orderSummaries = orders.stream().map(orderMapper::toSummary).toList();

        //create payment
        PaymentResponse paymentResponse = paymentService.createPayment(checkoutGroup.getId(), user.getId(), checkoutTotal, request.method());

        return CheckoutResponse.builder()
                .id(checkoutGroup.getId())
                .totalAmount(checkoutTotal)
                .expiredAt(expiredAt)
                .orders(orderSummaries)
                .status(CheckoutGroupStatus.PENDING_PAYMENT)
                .paymentResponse(paymentResponse)
                .build();
    }
```

## 2. Scheduler chỉ điều phối, service xử lý transaction từng checkout

File: src/main/java/com/moldy/moldymarket/order/jobs/CheckoutExpirationScheduler.java. Mỗi lượt dùng một cutoff. Một checkout lỗi được log và không chặn checkout tiếp theo. Không gọi method expireExpiredCheckouts không tồn tại của nhánh gốc.

```java
package com.moldy.moldymarket.order.jobs;

import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.service.CheckoutExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "checkout.expiration.enabled", havingValue = "true", matchIfMissing = true)
public class CheckoutExpirationScheduler {
    private final CheckoutGroupRepository checkoutGroupRepository;
    private final CheckoutExpirationService checkoutExpirationService;

    @Scheduled(fixedDelayString = "${checkout.expiration.interval-ms:60000}")
    public void expireCheckouts() {
        Instant cutoff = Instant.now();
        for (UUID id : checkoutGroupRepository.findExpiredCheckoutIds(cutoff)) {
            try {
                checkoutExpirationService.expire(id, cutoff);
            } catch (RuntimeException error) {
                log.error("Failed to expire checkout {}", id, error);
            }
        }
    }
}
```

Trong CheckoutGroupRepository, query phải select c.id vì kiểu trả về là List<UUID>. Chỉ chọn checkout pending có deadline đã tới. CheckoutExpirationService.expire hiện có tiếp tục kiểm tra lại dưới khóa checkout/payment/product; giữ nguyên giỏ, bỏ qua COD, payment thành công và order đã tiến triển. Payment hết hạn tạm giữ FAILED theo contract local; chuẩn hóa EXPIRED sẽ xem cùng lớp payment ở lượt tiếp.

## 3. Không cho người bán làm mất hiệu lực reservation

ProductService.requiredEditable và changeStatus gọi requireNotReserved. Nhờ đó update/softDelete/ẩn tin đang được giữ trả PRODUCT_RESERVED. Quyền admin kiểm duyệt giữ theo flow hiện có.

```java
private void requireNotReserved(Product product) {
    if (product.isReservedAt(java.time.Instant.now())) {
        throw new AppException(ErrorCode.PRODUCT_RESERVED);
    }
}
```

## 4. Cách chạy và giới hạn

MoldyMarketApplication bật EnableScheduling. Job mặc định chạy mỗi 60000 ms; có thể tắt bằng checkout.expiration.enabled=false hoặc đổi interval qua checkout.expiration.interval-ms. Khi chạy full test dùng property tắt job nền; test scheduler vẫn gọi và kiểm tra trực tiếp hành vi của job.

RED: 6 failure tái hiện checkout không giữ hàng/không khóa và seller vẫn ẩn/xóa product đang giữ. GREEN: compile và 71 regression test checkout, scheduler, reservation, product, cart pass, không skip. Full Maven verify/package đã pass: 215 test, không failure/error, 214 pass và 1 test auth QA-010 đã bị disable từ trước. Ban đầu terminal thiếu cấu hình DB/SePay của IntelliJ; khi dùng đúng các biến và JVM property hiện có cho riêng tiến trình Maven thì app context và regression HTTP category chạy được. Không in/ghi secret vào source và không đổi datasource hoặc cấu hình IntelliJ. Chưa thử hai transaction cạnh tranh hoặc scheduler trên DB thật.

Profile của IntelliJ đang là mặc định. Environment variables cung cấp DB_URL/DB_USERNAME/DB_PASSWORD và JWT_SECRET; VM parameters cung cấp các property sePay.bank.account, sePay.bank.name, sePay.webhook.secret-key. Full test truyền các giá trị đã lưu vào tiến trình Maven, không lưu lại giá trị vào tài liệu này.

Còn một cảnh báo schema có sẵn: Payment khai báo index trên expires_at nhưng cột entity là expired_at. Application vẫn khởi tạo được; sửa index trong lượt payment tiếp theo. Không lấy build pass của phần giữ hàng làm bằng chứng flow thanh toán đã xong.

## 5. Phần tiếp theo

Chưa nối PaymentCompletedEvent để đổi online thành CONFIRMED/SOLD và xóa đúng cart item; chưa ghép hủy/COD settlement hoặc đổi request sang chỉ productId. Vẫn giữ nút Đặt hàng Frontend tắt. Lượt tiếp theo cần thống nhất khóa checkout/payment giữa webhook và expiration trước khi nối listener, chặn thanh toán trễ, giữ idempotency và bổ sung test rollback. Không coi lượt giữ hàng này là checkout/payment đã hoàn chỉnh.
