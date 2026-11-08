package com.moldy.moldymarket.order.entity;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Setter
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    UUID id;

    @Column(name = "order_code", nullable = false, updatable = false, unique = true, length = 10)
    String orderCode;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    UUID buyerId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    UUID sellerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "checkout_group_id", updatable = false)
    CheckoutGroup checkoutGroup;

    @Column(name = "store_id")
    UUID storeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "return_deadline", updatable = false)
    Instant returnDeadline;

    @Column(name = "paid_at")
    Instant paidAt;
    @Column(name = "expired_at")
    Instant expiredAt;
    @Column(name = "confirmed_at")
    Instant confirmedAt;
    @Column(name = "shipped_at")
    Instant shippedAt;
    @Column(name = "delivered_at")
    Instant deliveredAt;
    @Column(name = "completed_at")
    Instant completedAt;
    @Column(name = "cancelled_at")
    Instant cancelledAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Version
    Long version;

    @Column(name = "shipping_fee", nullable = false, updatable = false, precision = 19, scale = 2)
    BigDecimal shippingFee;

    @Column(name = "shop_discount", nullable = false, updatable = false, precision = 19, scale = 2)
    BigDecimal shopDiscount = BigDecimal.ZERO;


    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    List<OrderItem> orderItems = new ArrayList<>();

    @Column(name = "total_amount", nullable = false, updatable = false)
    BigDecimal totalAmount;

    @Column(name = "receiver_name", nullable = false, updatable = false)
    String receiverName;

    @Column(name = "receiver_phone", nullable = false, updatable = false, length = 20)
    String receiverPhone;

    @Column(name = "shipping_address", nullable = false, updatable = false, length = 500)
    String shippingAddress;

    @Column(name = "note", length = 500, updatable = false)
    String note;

    @Column(name = "cancel_reason", length = 500)
    String cancelReason;

    @Column(name = "order_status", nullable = false)
    OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @PrePersist
    public void onCreate(){
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    @PreUpdate
    public void onUpdate(){
        this.updatedAt = Instant.now();
    }

    public void addOrderItem(OrderItem item){
        orderItems.add(item);
        item.setOrder(this);
    }

    @Builder
    public Order(CheckoutGroup checkoutGroup, UUID buyerId, UUID sellerId, UUID storeId,
                 String orderCode, OrderStatus status, String receiverName, String receiverPhone,
                 String shippingAddress, String note, BigDecimal shippingFee, BigDecimal totalAmount){
        this.checkoutGroup = checkoutGroup;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.storeId = storeId;
        this.orderCode = orderCode;
        this.status = status;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.shippingAddress = shippingAddress;
        this.shippingFee = shippingFee;
        this.note = note;
        this.totalAmount = totalAmount;
//        this.shopDiscount = shopDiscount;
    }

    public void confirmReceived(){
        if(this.status != OrderStatus.DELIVERED) throw new AppException(ErrorCode.INVALID_ORDER_STATE);

        this.status = OrderStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.returnDeadline = this.completedAt.plus(7, ChronoUnit.DAYS);
    }

}
