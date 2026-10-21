package com.moldy.moldymarket.voucher.dto;

import com.moldy.moldymarket.voucher.model.DiscountType;
import com.moldy.moldymarket.voucher.model.Voucher;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;


@Getter
@NoArgsConstructor
public class VoucherCheckResponse {

    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Instant expiredAt;
    private boolean valid;

    public VoucherCheckResponse(Voucher voucher, boolean valid) {
        this.code = voucher.getCode();
        this.discountType = voucher.getDiscountType();
        this.discountValue = voucher.getDiscountValue();
        this.expiredAt = voucher.getExpiredAt();
        this.valid = valid;
    }
}
