package com.moldy.moldymarket.order.entity;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
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
@Table(name = "checkout_groups")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckoutGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    UUID id;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    UUID buyerId;

    @Column(name = "total_amount", nullable = false)
    BigDecimal totalAmount;

    @OneToMany(mappedBy = "checkoutGroup")
    List<Order> orders = new ArrayList<>();

    @Column(name = "platform_discount", updatable = false)
    BigDecimal platformDiscount;

    @Column(name = "status", nullable = false)
    CheckoutGroupStatus status = CheckoutGroupStatus.PENDING_PAYMENT;

    @Column(name = "expired_at", updatable = false)
    Instant expiredAt;



    @Builder
    public CheckoutGroup(UUID buyerId, BigDecimal totalAmount, Instant expiredAt) {
        this.buyerId = buyerId;
        this.totalAmount = totalAmount;
//        this.platformDiscount = platformDiscount;
        this.expiredAt = expiredAt;
    }

    public void markPaid(){
        if(this.status != CheckoutGroupStatus.PENDING_PAYMENT){
            throw new AppException(ErrorCode.CHECKOUT_GROUP_INVALID_STATUS);
        }
        this.status = CheckoutGroupStatus.PAID;
    }


    public void markExpired(){
        if(this.status != CheckoutGroupStatus.PENDING_PAYMENT){
            throw new AppException(ErrorCode.CHECKOUT_GROUP_INVALID_STATUS);
        }
        this.status = CheckoutGroupStatus.EXPIRED;
    }

    public void addOrder(Order order){
        if(order == null) throw new AppException(ErrorCode.ORDER_NOT_EXISTED);
        this.orders.add(order);
        order.setCheckoutGroup(this);
    }
}
