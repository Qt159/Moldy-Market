# Checkout/payment — Backend chọn lọc đã nối

Ngày 2026-11-20, branch codex/selective-checkout-integration, nền main dc11b09 và các thay đổi local đã có. Chọn các phần checkout/payment từ feature/dispute 4a58390, chỉnh theo quyết định của Chu. Source đã ghi trực tiếp; các block dưới là bản code hiện tại để đọc và học, không cần chép lại vào cùng file. Chưa commit hoặc push.

## 1. Quy tắc đã chốt

- Mỗi product chỉ mua 1; chặn seller và mọi owner/staff trong cùng gian hàng. Giá và buyer lấy từ server.
- Online giữ 15 phút. Backend nhận khoản đúng và còn hạn thì payment SUCCESSFUL, checkout PAID, mọi order CONFIRMED, product SOLD; xóa đúng item đã mua khỏi giỏ. Giỏ giữ item khác.
- Online chưa thanh toán: hủy một order hủy toàn bộ checkout dùng chung QR. Trả mọi reservation, giữ giỏ. Tiền đến sau khi hủy/hết hạn ghi LATE để đối soát, không tự bán một reservation mới hoặc giả lập refund.
- COD chờ chủ gian hàng xác nhận, kể cả tin do staff đăng. Admin chuyển SHIPPING, DELIVERED rồi ghi nhận thu tiền. Người mua được hủy trước SHIPPING khi chưa thu tiền; không phát refund COD.
- COD xóa item của từng order ngay khi admin xác nhận đã thu tiền. Payment/group chỉ hoàn tất khi mọi order không hủy đã được thu tiền. Hủy một phần COD làm giảm đúng tổng tiền còn phải trả, giữ các order khác.
- Hủy online đã trả tiền, xử lý refund/escrow, tự xác nhận đã nhận hàng online và API danh sách order phía gian hàng là các phần riêng; không ghép các stub dispute/refund chưa compile.

## 2. Cách đọc flow

1. CheckoutGroupService: kiểm tra request, khóa product theo ID tăng dần, chặn tự mua/reservation, lấy snapshot giá và giữ hàng cùng transaction tạo order/payment.
2. PaymentTransactionService: lưu receipt có unique provider/externalId, tìm checkout ID bằng projection, khóa checkout rồi payment. Sai số tiền/tiền ra/trùng/trễ không xác nhận đơn.
3. PaymentCompletedEventListener gọi CheckoutPaymentService ngay trong transaction nhận tiền. Lỗi ở product/order/cart làm rollback receipt và payment cùng nhau, để có thể retry. Không chuyển listener sang AFTER_COMMIT.
4. CheckoutPaymentService: kiểm tra payment thực tế đã SUCCESSFUL và còn hạn, khóa product, xác nhận order, bán đúng reservation rồi gọi CartService.removePurchasedItems bằng ID đọc từ order đã lưu.
5. OrderService: kiểm tra buyer/owner/admin, hủy hoặc xử lý COD. Mọi action cùng khóa checkout → payment → product để không đảo thứ tự với webhook/job.

Payment là bản ghi tiền; checkout là nhóm đơn dùng chung một lần thanh toán; order là phần hàng theo seller/store. Vì vậy COD có thể có một order đã thu tiền trong khi payment của cả nhóm vẫn PENDING.

## 3. Request và tạo payment

Frontend mới gửi orderItems: [{ productId }]. quantity được mặc định bằng 1; request cũ gửi quantity:1 vẫn hợp lệ, giá trị lớn hơn 1 vẫn bị chặn. Thông tin nhận hàng giữ receiverName, phoneNumber, shippingAddress, method và note. Không gửi giá/userId.

```java
package com.moldy.moldymarket.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderItemRequest(
        @NotNull
        UUID productId,
        @Min(1)
        @Max(1)
        Integer quantity) {
    public OrderItemRequest {
        if (quantity == null) quantity = 1;
    }
}
```

Payment lấy buyer và totalAmount từ checkout đã lưu:

```java
@Transactional
    public PaymentResponse createPayment(UUID checkoutGroupId, PaymentMethod method){
        var checkoutGroup = checkoutGroupRepository.findById(checkoutGroupId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        CheckoutGroupStatus expected = method == PaymentMethod.COD
                ? CheckoutGroupStatus.PAY_ON_DELIVERY : CheckoutGroupStatus.PENDING_PAYMENT;
        if(checkoutGroup.getStatus() != expected) throw new AppException(ErrorCode.CHECKOUT_GROUP_INVALID_STATUS);
        //prevent retry
        var existedPayment = paymentRepository.findByCheckoutGroupId(checkoutGroup.getId());
        if(existedPayment.isPresent()) return paymentMapper.toPaymentResponse(existedPayment.get());

        String qrBankUrl = null, paymentCode = null;
        if(method == PaymentMethod.ONLINE_BANKING){
            paymentCode = generatePaymentCode();
            qrBankUrl = createQrBankUrl(paymentCode, checkoutGroup.getTotalAmount());
        }

        Payment payment = Payment.builder()
                .checkoutGroupId(checkoutGroup.getId())
                .payerId(checkoutGroup.getBuyerId())
                .amount(checkoutGroup.getTotalAmount())
                .method(method)
                .paymentCode(paymentCode)
                .qrBankUrl(qrBankUrl)
                .expiredAt(checkoutGroup.getExpiredAt())
                .build();
        return paymentMapper.toPaymentResponse(paymentRepository.save(payment));
    }
```

## 4. Nhận tiền và xác nhận online

Cùng một transfer không được xử lý hai lần. Một transfer khác trả vào payment đã thành công được lưu DUPLICATE, không xóa giỏ hoặc bán hàng lần nữa. Hạn tính theo thời điểm Backend xử lý dưới khóa; giao dịch đến muộn vẫn có receipt để đối soát.

```java
package com.moldy.moldymarket.payment.service;

import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.entity.PaymentTransaction;
import com.moldy.moldymarket.payment.enums.MatchStatus;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.enums.TransferType;
import com.moldy.moldymarket.payment.event.PaymentCompletedEvent;
import com.moldy.moldymarket.payment.mapper.PaymentTransactionMapper;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.payment.repository.PaymentTransactionRepository;
import com.moldy.moldymarket.payment.webhook.IncomingTransfer;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.service.CheckoutExpirationService;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentTransactionService {
    static Pattern CODE_PATTERN = Pattern.compile("MM[A-Z0-9]{8}", Pattern.CASE_INSENSITIVE);
    PaymentRepository paymentRepository;
    PaymentTransactionRepository paymentTransactionRepository;
    ApplicationEventPublisher events;
    PaymentTransactionMapper paymentTransactionMapper;
    CheckoutGroupRepository checkoutGroupRepository;
    CheckoutExpirationService checkoutExpirationService;

    @Transactional(timeout = 5)
    public void process(IncomingTransfer incomingTransfer){
        //idempotency
        PaymentTransaction paymentTransaction = paymentTransactionRepository.saveAndFlush(paymentTransactionMapper.toPaymentTransaction(incomingTransfer));

        //record only: our own payouts and refunds
        if(incomingTransfer.type() == TransferType.OUT){
            paymentTransaction.recordMatch(null, MatchStatus.IGNORED);
            return;
        }

        //find the payment by its code
        String code = resolveCode(incomingTransfer);
        var checkoutId = code == null ? null : paymentRepository.findCheckoutGroupIdByPaymentCode(code).orElse(null);
        if(checkoutId == null){
            paymentTransaction.recordMatch(code, MatchStatus.UNMATCHED);
            return;
        }

        // Match the expiry/cancellation lock order; do not preload a stale Payment entity.
        var checkout = checkoutGroupRepository.findByIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_GROUP_NOT_EXISTED));
        Payment payment = paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));
        if (payment.getMethod() != PaymentMethod.ONLINE_BANKING) {
            paymentTransaction.recordMatch(code, MatchStatus.UNMATCHED);
            return;
        }

        //check the received amount if it's equal to amount of saved payment
        if(payment.getAmount().compareTo(incomingTransfer.amount()) != 0){
            paymentTransaction.recordMatch(code, MatchStatus.AMOUNT_MISMATCH);
            log.warn("Amount mistmatch: paymentId = {}, expected={}, received={}, externalId={}",
                    payment.getId(), payment.getAmount(), incomingTransfer.amount(), incomingTransfer.externalId());
            return;
        }

        // Pending -> Successful
        Instant now = Instant.now();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            paymentTransaction.recordMatch(code, payment.getStatus() == PaymentStatus.SUCCESSFUL
                    ? MatchStatus.DUPLICATE : MatchStatus.LATE);
            return;
        }
        if (checkout.getStatus() != CheckoutGroupStatus.PENDING_PAYMENT
                || checkout.getExpiredAt() == null || !now.isBefore(checkout.getExpiredAt())) {
            checkoutExpirationService.expire(checkoutId, now);
            paymentTransaction.recordMatch(code, MatchStatus.LATE);
            return;
        }

        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setPaidAt(now);
        payment.setUpdatedAt(now);
        payment.setPaymentTransaction(paymentTransaction);
        paymentTransaction.recordMatch(code, MatchStatus.MATCHED);
        events.publishEvent(new PaymentCompletedEvent(
                payment.getId(), payment.getCheckoutGroupId(), payment.getPayerId(), payment.getAmount(), now));
        //todo: late/duplicate -> refund or re-reserve for buyers
    }

    @Transactional(readOnly = true)
    public boolean alreadyReceived(String provider, String externalId){
        return paymentTransactionRepository.existsByProviderAndExternalId(provider, externalId);
    }

    private String resolveCode(IncomingTransfer incomingTransfer){
        if (incomingTransfer.paymentCode() != null && !incomingTransfer.paymentCode().isBlank()) {
            return incomingTransfer.paymentCode().trim().toUpperCase();
        }
        if (incomingTransfer.content() == null) return null;
        Matcher m = CODE_PATTERN.matcher(incomingTransfer.content());
        return m.find() ? m.group().toUpperCase() : null;
    }

}
```

```java
package com.moldy.moldymarket.order.listener;

import com.moldy.moldymarket.order.service.CheckoutPaymentService;
import com.moldy.moldymarket.payment.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCompletedEventListener {
    private final CheckoutPaymentService checkoutPaymentService;

    // Synchronous: a failed order/cart update must roll back the incoming payment too.
    @EventListener
    public void handle(PaymentCompletedEvent event) {
        checkoutPaymentService.confirmOnline(event.checkoutGroupId());
    }
}
```

```java
package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.cart.service.CartService;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutPaymentService {
    private final CheckoutGroupRepository checkoutGroupRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmOnline(UUID checkoutId) {
        var checkout = checkoutGroupRepository.findByIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_GROUP_NOT_EXISTED));
        var payment = paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));
        if (payment.getMethod() != PaymentMethod.ONLINE_BANKING
                || payment.getStatus() != PaymentStatus.SUCCESSFUL) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }
        if (checkout.getStatus() == CheckoutGroupStatus.PAID) return;
        var paidAt = payment.getPaidAt();
        if (checkout.getStatus() != CheckoutGroupStatus.PENDING_PAYMENT || paidAt == null
                || checkout.getExpiredAt() == null || !paidAt.isBefore(checkout.getExpiredAt())
                || !checkout.getBuyerId().equals(payment.getPayerId())
                || checkout.getTotalAmount().compareTo(payment.getAmount()) != 0
                || checkout.getOrders().isEmpty()
                || checkout.getOrders().stream().anyMatch(order -> order.getStatus() != OrderStatus.PENDING_PAYMENT)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }
        var productIds = checkout.getOrders().stream().flatMap(order -> order.getOrderItems().stream())
                .map(item -> item.getProductId()).distinct().sorted().toList();
        for (UUID productId : productIds) {
            var product = productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
            product.completeReservationFor(checkoutId, paidAt);
        }
        for (var order : checkout.getOrders()) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaidAt(paidAt);
            order.setConfirmedAt(paidAt);
            order.setUpdatedAt(paidAt);
        }
        checkout.markPaid();
        cartService.removePurchasedItems(checkout.getBuyerId(), productIds);
    }
}
```

## 5. COD, hủy và quyền

Buyer chỉ đọc/hủy order của mình. Chủ store xác nhận order từ gian hàng của mình; staff đăng product không tự có quyền xác nhận. Với dữ liệu cá nhân cũ không có storeId, kiểm tra sellerId để giữ khả năng xử lý order cũ; đây không phải API mở bán cá nhân mới. Admin shipping/delivery dùng chung cho order đã CONFIRMED; ghi nhận COD chỉ nhận payment COD và order DELIVERED. Online DELIVERED không tự chuyển COMPLETED.

```java
package com.moldy.moldymarket.order.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.dto.response.OrderResponse;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.mapper.OrderMapper;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.cart.service.CartService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService {
    OrderRepository orderRepository;
    CurrentUser currentUser;
    OrderMapper orderMapper;
    CheckoutGroupRepository checkoutGroupRepository;
    PaymentRepository paymentRepository;
    ProductRepository productRepository;
    StoreRepository storeRepository;
    CartService cartService;

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrderByBuyerId(){
        var user = currentUser.findCurrentUser();
        var orders = orderRepository.findAllByBuyerId(user.getId());
        if(orders.isEmpty())
            throw new AppException(ErrorCode.ORDER_LIST_NOT_FOUND);
        return orders.stream().map(orderMapper::toOrderResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId){
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        requireBuyer(order);
        return orderMapper.toOrderResponse(order);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse receivedCOD(UUID orderId){
        var locked = lockOrder(orderId);
        var order = locked.order();
        if (locked.payment().getMethod() != PaymentMethod.COD) throw invalidState();
        if (order.getPaidAt() != null) return orderMapper.toOrderResponse(order);
        if (order.getStatus() != OrderStatus.DELIVERED) throw new AppException(ErrorCode.ORDER_NOT_DELIVERED);
        if (locked.checkout().getStatus() != CheckoutGroupStatus.PAY_ON_DELIVERY
                || locked.payment().getStatus() != PaymentStatus.PENDING) throw invalidState();
        Instant now = Instant.now();
        var ids = productIds(List.of(order));
        for (UUID productId : ids) {
            productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND))
                    .completeReservationFor(locked.checkout().getId(), now);
        }
        order.confirmReceived();
        order.setPaidAt(now);
        updateCodGroup(locked.checkout(), locked.payment(), now);
        cartService.removePurchasedItems(order.getBuyerId(), ids);
        return orderMapper.toOrderResponse(order);
    }

    @Transactional
    public OrderResponse confirmOrder(UUID orderId) {
        var locked = lockOrder(orderId);
        var order = locked.order();
        UUID userId = currentUser.findCurrentUser().getId();
        boolean isOwner = order.getStoreId() == null
                ? order.getSellerId().equals(userId)
                : storeRepository.findById(order.getStoreId())
                    .map(store -> store.getOwnerId().equals(userId)).orElse(false);
        if (!isOwner) throw new AppException(ErrorCode.ORDER_NOT_EXISTED);
        if (locked.payment().getMethod() != PaymentMethod.COD) throw invalidState();
        if (order.getStatus() == OrderStatus.CONFIRMED) return orderMapper.toOrderResponse(order);
        if (order.getStatus() != OrderStatus.PENDING_CONFIRMED) throw invalidState();
        order.setStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(Instant.now());
        return orderMapper.toOrderResponse(order);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse startShipping(UUID orderId) {
        var order = lockOrder(orderId).order();
        if (order.getStatus() == OrderStatus.SHIPPING) return orderMapper.toOrderResponse(order);
        if (order.getStatus() != OrderStatus.CONFIRMED) throw invalidState();
        order.setStatus(OrderStatus.SHIPPING);
        order.setShippedAt(Instant.now());
        return orderMapper.toOrderResponse(order);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse markDelivered(UUID orderId) {
        var order = lockOrder(orderId).order();
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.COMPLETED)
            return orderMapper.toOrderResponse(order);
        if (order.getStatus() != OrderStatus.SHIPPING) throw invalidState();
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(Instant.now());
        return orderMapper.toOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrderFromBuyer(UUID orderId) {
        var locked = lockOrder(orderId);
        var order = locked.order();
        requireBuyer(order);
        if (order.getStatus() == OrderStatus.CANCELLED) return orderMapper.toOrderResponse(order);
        if (locked.payment().getStatus() != PaymentStatus.PENDING) throw invalidState();
        Instant now = Instant.now();
        List<Order> cancelled;
        if (locked.payment().getMethod() == PaymentMethod.ONLINE_BANKING) {
            if (locked.checkout().getStatus() != CheckoutGroupStatus.PENDING_PAYMENT
                    || locked.checkout().getOrders().stream().anyMatch(o -> o.getStatus() != OrderStatus.PENDING_PAYMENT))
                throw invalidState();
            // One bank QR pays the entire group; cancelling any unpaid order invalidates it all.
            cancelled = locked.checkout().getOrders();
        } else {
            if (locked.checkout().getStatus() != CheckoutGroupStatus.PAY_ON_DELIVERY
                    || (order.getStatus() != OrderStatus.PENDING_CONFIRMED && order.getStatus() != OrderStatus.CONFIRMED))
                throw invalidState();
            cancelled = List.of(order);
        }
        for (UUID productId : productIds(cancelled)) {
            productRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND))
                    .releaseReservationFor(locked.checkout().getId(), now);
        }
        for (Order item : cancelled) {
            item.setStatus(OrderStatus.CANCELLED);
            item.setCancelledAt(now);
            item.setUpdatedAt(now);
        }
        if (locked.payment().getMethod() == PaymentMethod.ONLINE_BANKING) {
            locked.checkout().setStatus(CheckoutGroupStatus.CANCELLED);
            locked.payment().setStatus(PaymentStatus.FAILED);
            locked.payment().setUpdatedAt(now);
        } else {
            updateCodGroup(locked.checkout(), locked.payment(), now);
        }
        return orderMapper.toOrderResponse(order);
    }

    private LockedOrder lockOrder(UUID orderId) {
        UUID checkoutId = orderRepository.findCheckoutGroupIdByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        var checkout = checkoutGroupRepository.findByIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_GROUP_NOT_EXISTED));
        var payment = paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));
        var order = checkout.getOrders().stream().filter(item -> orderId.equals(item.getId()))
                .findFirst().orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        return new LockedOrder(checkout, order, payment);
    }

    private void requireBuyer(Order order) {
        if (!order.getBuyerId().equals(currentUser.findCurrentUser().getId()))
            throw new AppException(ErrorCode.ORDER_NOT_EXISTED);
    }

    private List<UUID> productIds(List<Order> orders) {
        return orders.stream().flatMap(order -> order.getOrderItems().stream())
                .map(item -> item.getProductId()).distinct().sorted().toList();
    }

    private void updateCodGroup(CheckoutGroup checkout, Payment payment, Instant now) {
        var payable = checkout.getOrders().stream().filter(order -> order.getStatus() != OrderStatus.CANCELLED).toList();
        BigDecimal total = payable.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        checkout.setTotalAmount(total);
        payment.setAmount(total);
        payment.setUpdatedAt(now);
        if (payable.isEmpty()) {
            checkout.setStatus(CheckoutGroupStatus.CANCELLED);
            payment.setStatus(PaymentStatus.FAILED);
        } else if (payable.stream().allMatch(order -> order.getPaidAt() != null)) {
            checkout.markPaid();
            payment.setStatus(PaymentStatus.SUCCESSFUL);
            payment.setPaidAt(payable.stream().map(Order::getPaidAt).max(Instant::compareTo).orElseThrow());
        }
    }

    private AppException invalidState() { return new AppException(ErrorCode.INVALID_ORDER_STATE); }
    private record LockedOrder(CheckoutGroup checkout, Order order, Payment payment) {}
}
```

Các route action đều lấy từ branch đã đối chiếu:

- POST /api/v1/orders/buyer/{orderId}/cancel
- POST /api/v1/orders/seller/{orderId}/confirm
- PATCH /api/v1/orders/admin/{orderId}/start-shipping
- PATCH /api/v1/orders/admin/{orderId}/mark-delivered
- POST /api/v1/orders/admin/{orderId}/cod-collected

Các route hiện có giữ nguyên: POST /api/v1/checkout-groups, GET /api/v1/checkout-groups/{id}, GET /api/v1/payments/{id}, GET /api/v1/orders/me và GET /api/v1/orders/{orderId}. GET order detail nay kiểm tra buyer. Endpoint webhook là POST /api/v1/payment_transactions/webhook/sepay, không phải endpoint để Frontend gọi xác nhận chuyển khoản.

PaymentResponse trả id, amount, paymentStatus, method (COD/ONLINE_BANKING), qrBankUrl và checkoutGroupId. Dùng method để xác định flow sau reload.

## 6. Mapper và DB

PaymentMapper map status → paymentStatus. OrderMapper map checkoutGroup.id → checkoutGroupId và order.id → orderId của item. Test đã tái hiện các field null trước khi sửa.

DB lưu enum order/checkout bằng số. Các giá trị cũ giữ nguyên: CONFIRMED=1, REFUNDED=8, PAID=1. Chỉ append PENDING_CONFIRMED=9 và PAY_ON_DELIVERY=4. Migration mở rộng constraint, không UPDATE trạng thái các đơn cũ; index payment sửa sang cột expired_at có thật. Đã áp dụng lên DB local. Repo chưa có runner Flyway, nên khi dùng một DB đã tồn tại ở máy khác phải chạy SQL này trong transaction trước khi chạy bản mới.

```sql
-- Existing enum ordinals are preserved; new values are appended only.
ALTER TABLE public.orders DROP CONSTRAINT IF EXISTS orders_order_status_check;
ALTER TABLE public.orders ADD CONSTRAINT orders_order_status_check
    CHECK (order_status BETWEEN 0 AND 9);
ALTER TABLE public.checkout_groups DROP CONSTRAINT IF EXISTS checkout_groups_status_check;
ALTER TABLE public.checkout_groups ADD CONSTRAINT checkout_groups_status_check
    CHECK (status BETWEEN 0 AND 4);
CREATE INDEX IF NOT EXISTS idx_payments_status_expires ON public.payments (status, expired_at);
```

## 7. Đã kiểm tra gì

- Full Maven verify/package: 264 test, 263 pass, không failure/error; 1 test auth QA-010 đã disabled từ trước. Không thêm skip.
- 8 integration test PostgreSQL/HTTP: online nhiều order; rollback khi cart ghi lỗi và retry cùng receipt; tiền trễ; COD đủ confirmation/delivery/collection; hai buyer tranh một product; hủy COD một phần và lưu đúng tổng; hủy online rồi giữ mới không bị tiền cũ bán mất; HTTP webhook có chữ ký thật theo test secret, chặn sửa body và acknowledge retry. Dữ liệu fixture dọn theo ID.
- Unit test quyền owner/buyer, không refund COD, trạng thái/timestamp, ordinal cũ, mapper, số tiền VND không nguyên và timestamp webhook lỗi.
- Đã đảo tạm guard hết hạn để kiểm tra test bắt lỗi, sau đó khôi phục source nguyên bytes và 7 test webhook service pass lại.
- Chưa có chuyển khoản thật từ ngân hàng/SePay Live; test HTTP dùng payload ký bằng test secret. Scheduler được test trực tiếp và tắt trong full test để tránh job nền ảnh hưởng fixture.

Lệnh verify dùng cấu hình DB/JWT và các property SePay đã lưu trong IntelliJ, chỉ truyền cho tiến trình Maven. Không ghi giá trị secret vào source/tài liệu và không đổi cấu hình đã lưu.

## 8. Việc tiếp theo

Chu restart Backend khi muốn thử bản vừa sửa. Sau khi duyệt Backend, nối Frontend submit checkout → trang QR/COD → polling trạng thái server → refresh giỏ/đơn. Giữ nút Đặt hàng tắt cho đến khi thêm service/mutation và UI xử lý lỗi. Không retry POST checkout tự động vì request có thể đã tạo đơn trước khi mất mạng.

## Tài liệu SePay đã đối chiếu

HMAC ký timestamp và raw body, kèm X-SePay-Signature/X-SePay-Timestamp: [xác thực webhook](https://developer.sepay.vn/vi/sepay-webhooks/xac-thuc). Phản hồi JSON success và retry: [hướng dẫn webhook](https://docs.sepay.vn/tich-hop-webhooks.html). Khi cấu hình thật cần chọn đúng account, mã thanh toán MM và HMAC tương ứng; các test local không thay thế việc kiểm tra webhook từ SePay.
