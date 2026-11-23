package com.moldy.moldymarket.voucher.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.store.entity.Store;
import com.moldy.moldymarket.store.entity.StoreStatus;
import com.moldy.moldymarket.store.repository.StoreRepository;
import com.moldy.moldymarket.voucher.dto.CreateVoucherRequest;
import com.moldy.moldymarket.voucher.dto.UpdateVoucherRequest;
import com.moldy.moldymarket.voucher.dto.VoucherCheckResponse;
import com.moldy.moldymarket.voucher.dto.VoucherResponse;
import com.moldy.moldymarket.voucher.model.DiscountType;
import com.moldy.moldymarket.voucher.model.Voucher;
import com.moldy.moldymarket.voucher.model.VoucherIssuer;
import com.moldy.moldymarket.voucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final StoreRepository storeRepository;

    @Transactional
    public VoucherResponse createVoucher(CreateVoucherRequest request) {
        return create(request, VoucherIssuer.PLATFORM, null);
    }

    @Transactional
    public VoucherResponse createStoreVoucher(CreateVoucherRequest request, UUID ownerId) {
        Store store = getActiveOwnerStore(ownerId);
        return create(request, VoucherIssuer.STORE, store);
    }

    private VoucherResponse create(CreateVoucherRequest request, VoucherIssuer issuer, Store store) {
        validateConfiguration(request.getDiscountType(), request.getDiscountValue(),
                request.getExpiredAt(), request.getMinimumOrderValue(), request.getMaximumDiscount());
        String code = normalizeCode(request.getCode());
        if (voucherRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.VOUCHER_CODE_ALREADY_EXISTS);
        }
        Voucher voucher = new Voucher();
        voucher.setCode(code);
        voucher.setIssuer(issuer);
        voucher.setStore(store);
        voucher.setDiscountType(request.getDiscountType());
        voucher.setDiscountValue(request.getDiscountValue());
        voucher.setRemainingQuantity(request.getRemainingQuantity());
        voucher.setExpiredAt(request.getExpiredAt());
        voucher.setMinimumOrderValue(request.getMinimumOrderValue());
        voucher.setMaximumDiscount(request.getMaximumDiscount());
        try {
            Voucher saved = voucherRepository.saveAndFlush(voucher);
            log.info("Voucher created. code={}, issuer={}", code, issuer);
            return new VoucherResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.VOUCHER_CODE_ALREADY_EXISTS);
        }
    }

    public VoucherCheckResponse checkVoucher(String rawCode) {
        Voucher voucher = findByCode(rawCode);
        boolean valid = voucher.isActive()
                && voucher.getExpiredAt().isAfter(Instant.now())
                && voucher.getRemainingQuantity() > 0;
        return new VoucherCheckResponse(voucher, valid);
    }

    public List<VoucherResponse> getAllVouchers() {
        return voucherRepository.findAll().stream().map(VoucherResponse::new).toList();
    }

    public VoucherResponse getVoucherByCode(String code) {
        return new VoucherResponse(findByCode(code));
    }

    public List<VoucherResponse> getMyStoreVouchers(UUID ownerId) {
        Store store = getActiveOwnerStore(ownerId);
        return voucherRepository.findAllByStore_IdOrderByCreatedAtDesc(store.getId())
                .stream().map(VoucherResponse::new).toList();
    }

    public VoucherResponse getMyStoreVoucher(String code, UUID ownerId) {
        return new VoucherResponse(findOwnedStoreVoucher(code, ownerId));
    }

    @Transactional
    public VoucherResponse updateMyStoreVoucher(String code, UUID ownerId, UpdateVoucherRequest request) {
        Store store = getActiveOwnerStore(ownerId);
        Voucher voucher = voucherRepository.findByCodeForUpdate(normalizeCode(code))
                .filter(v -> v.getIssuer() == VoucherIssuer.STORE
                        && v.getStore() != null && v.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        validateConfiguration(request.discountType(), request.discountValue(), request.expiredAt(),
                request.minimumOrderValue(), request.maximumDiscount());
        voucher.setDiscountType(request.discountType());
        voucher.setDiscountValue(request.discountValue());
        voucher.setRemainingQuantity(request.remainingQuantity());
        voucher.setExpiredAt(request.expiredAt());
        voucher.setMinimumOrderValue(request.minimumOrderValue());
        voucher.setMaximumDiscount(request.maximumDiscount());
        return new VoucherResponse(voucherRepository.save(voucher));
    }

    @Transactional
    public void deactivateMyStoreVoucher(String code, UUID ownerId) {
        Voucher voucher = findOwnedStoreVoucherForUpdate(code, ownerId);
        voucher.setActive(false);
        voucherRepository.save(voucher);
    }

    public Voucher requirePlatformVoucher(String code) {
        Voucher voucher = validateAndGet(code);
        if (voucher.getIssuer() != VoucherIssuer.PLATFORM || voucher.getStore() != null) {
            throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }
        return voucher;
    }

    public Voucher requireStoreVoucher(String code, UUID expectedStoreId) {
        Voucher voucher = validateAndGet(code);
        if (voucher.getIssuer() != VoucherIssuer.STORE || voucher.getStore() == null) {
            throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }
        if (expectedStoreId == null || !voucher.getStore().getId().equals(expectedStoreId)) {
            throw new AppException(ErrorCode.VOUCHER_STORE_MISMATCH);
        }
        return voucher;
    }

    public Voucher requireStoreVoucher(String code) {
        Voucher voucher = validateAndGet(code);
        if (voucher.getIssuer() != VoucherIssuer.STORE || voucher.getStore() == null) {
            throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }
        return voucher;
    }

    public Voucher validateAndGet(String code) {
        Voucher voucher = findByCode(code);
        if (!voucher.isActive()) throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);
        if (!voucher.getExpiredAt().isAfter(Instant.now())) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        }
        if (voucher.getRemainingQuantity() <= 0) {
            throw new AppException(ErrorCode.VOUCHER_OUT_OF_STOCK);
        }
        return voucher;
    }

    @Transactional
    public void consumeVoucher(Voucher voucher, boolean reserveForOnlinePayment) {
        int updated = reserveForOnlinePayment
                ? voucherRepository.reserveOne(voucher.getId())
                : voucherRepository.consumeOne(voucher.getId());
        if (updated != 1) throw new AppException(ErrorCode.VOUCHER_OUT_OF_STOCK);
    }

    @Transactional
    public void finalizeReservation(UUID voucherId) {
        if (voucherRepository.finalizeReservation(voucherId) != 1) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    @Transactional
    public void releaseReservation(UUID voucherId) {
        if (voucherRepository.releaseReservation(voucherId) != 1) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    @Transactional
    public void restoreConsumed(UUID voucherId) {
        voucherRepository.restoreOne(voucherId);
    }

    public BigDecimal calculateDiscount(Voucher voucher, BigDecimal eligibleAmount) {
        if (eligibleAmount.signum() < 0) throw new IllegalArgumentException("Eligible amount cannot be negative");
        if (voucher.getMinimumOrderValue() != null
                && eligibleAmount.compareTo(voucher.getMinimumOrderValue()) < 0) {
            throw new AppException(ErrorCode.VOUCHER_MINIMUM_ORDER_NOT_MET);
        }
        BigDecimal discount = voucher.getDiscountType() == DiscountType.PERCENT
                ? eligibleAmount.multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN)
                : voucher.getDiscountValue();
        if (voucher.getMaximumDiscount() != null) {
            discount = discount.min(voucher.getMaximumDiscount());
        }
        return discount.min(eligibleAmount).max(BigDecimal.ZERO);
    }

    private Voucher findOwnedStoreVoucher(String code, UUID ownerId) {
        Store store = getActiveOwnerStore(ownerId);
        return voucherRepository.findByCode(normalizeCode(code))
                .filter(v -> v.getIssuer() == VoucherIssuer.STORE && v.getStore() != null
                        && v.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
    }

    private Voucher findOwnedStoreVoucherForUpdate(String code, UUID ownerId) {
        Store store = getActiveOwnerStore(ownerId);
        return voucherRepository.findByCodeForUpdate(normalizeCode(code))
                .filter(v -> v.getIssuer() == VoucherIssuer.STORE && v.getStore() != null
                        && v.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
    }

    private Store getActiveOwnerStore(UUID ownerId) {
        Store store = storeRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_FOUND));
        if (store.getStatus() == StoreStatus.BLOCKED) throw new AppException(ErrorCode.STORE_BLOCKED);
        return store;
    }

    private Voucher findByCode(String code) {
        return voucherRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
    }

    public static String normalizeCode(String code) {
        if (code == null || code.isBlank()) throw new AppException(ErrorCode.INVALID_REQUEST);
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void validateConfiguration(DiscountType type, BigDecimal value, Instant expiredAt,
            BigDecimal minimumOrderValue, BigDecimal maximumDiscount) {
        if (type == null || value == null || value.signum() <= 0 || expiredAt == null
                || !expiredAt.isAfter(Instant.now())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Voucher values and expiry must be valid and future dated");
        }
        if (type == DiscountType.PERCENT && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Percentage discount cannot exceed 100");
        }
        if (minimumOrderValue != null && minimumOrderValue.signum() < 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Minimum order value cannot be negative");
        }
        if (maximumDiscount != null && (maximumDiscount.signum() <= 0 || type != DiscountType.PERCENT)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Maximum discount is only valid for percentage vouchers");
        }
    }
}
