package com.moldy.moldymarket.voucher.dto;

import com.moldy.moldymarket.voucher.model.DiscountType;
import com.moldy.moldymarket.voucher.model.Voucher;
import com.moldy.moldymarket.voucher.model.VoucherIssuer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VoucherResponse {

    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Integer remainingQuantity;
    private Instant expiredAt;
    private VoucherIssuer issuer;
    private UUID storeId;
    private BigDecimal minimumOrderValue;
    private BigDecimal maximumDiscount;
    private boolean active;

    public VoucherResponse(Voucher voucher) {
        this.code = voucher.getCode();
        this.discountType = voucher.getDiscountType();
        this.discountValue = voucher.getDiscountValue();
        this.remainingQuantity = voucher.getRemainingQuantity();
        this.expiredAt = voucher.getExpiredAt();
        this.issuer = voucher.getIssuer();
        this.storeId = voucher.getStore() == null ? null : voucher.getStore().getId();
        this.minimumOrderValue = voucher.getMinimumOrderValue();
        this.maximumDiscount = voucher.getMaximumDiscount();
        this.active = voucher.isActive();
    }
}
