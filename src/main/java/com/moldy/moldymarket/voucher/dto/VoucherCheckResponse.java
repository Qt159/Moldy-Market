package com.moldy.moldymarket.voucher.dto;

import com.moldy.moldymarket.voucher.model.DiscountType;
import com.moldy.moldymarket.voucher.model.Voucher;
import com.moldy.moldymarket.voucher.model.VoucherIssuer;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class VoucherCheckResponse {

    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Instant expiredAt;
    private boolean valid;
    private VoucherIssuer issuer;
    private UUID storeId;

    public VoucherCheckResponse(Voucher voucher, boolean valid) {
        this.code = voucher.getCode();
        this.discountType = voucher.getDiscountType();
        this.discountValue = voucher.getDiscountValue();
        this.expiredAt = voucher.getExpiredAt();
        this.valid = valid;
        this.issuer = voucher.getIssuer();
        this.storeId = voucher.getStore() == null ? null : voucher.getStore().getId();
    }
}
