package com.moldy.moldymarket.order.entity;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


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

    @Column(name = "payment_code", nullable = false, updatable = false)
    String paymentCode;

    @Column(name = "total_amount", nullable = false, updatable = false)
    BigDecimal totalAmount;

    @Column(name = "platform_discount", nullable = false, updatable = false)
    BigDecimal platformDiscount;

    @Column(name = "status", nullable = false)
    CheckoutGroupStatus status;

    @Column(name = "expired_at", nullable = false, updatable = false)
    Instant expiredAt;

    @Builder
    public CheckoutGroup(UUID buyerId, String paymentCode, BigDecimal totalAmount, BigDecimal platformDiscount, CheckoutGroupStatus status, Instant expiredAt) {
        this.buyerId = buyerId;
        this.totalAmount = totalAmount;
        this.paymentCode = paymentCode;
        this.platformDiscount = platformDiscount;
        this.status = status;
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
}
