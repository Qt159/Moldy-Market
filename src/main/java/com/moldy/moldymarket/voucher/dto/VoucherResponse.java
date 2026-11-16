package com.moldy.moldymarket.voucher.dto;

import com.moldy.moldymarket.voucher.model.DiscountType;
import com.moldy.moldymarket.voucher.model.Voucher;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VoucherResponse {

    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Integer remainingQuantity;
    private Instant expiredAt;

    public VoucherResponse(Voucher voucher) {
        this.code = voucher.getCode();
        this.discountType = voucher.getDiscountType();
        this.discountValue = voucher.getDiscountValue();
        this.remainingQuantity = voucher.getRemainingQuantity();
        this.expiredAt = voucher.getExpiredAt();
    }
}
