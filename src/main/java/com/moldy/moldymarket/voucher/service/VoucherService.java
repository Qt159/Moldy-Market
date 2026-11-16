package com.moldy.moldymarket.voucher.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.voucher.dto.CreateVoucherRequest;
import com.moldy.moldymarket.voucher.dto.VoucherCheckResponse;
import com.moldy.moldymarket.voucher.dto.VoucherResponse;
import com.moldy.moldymarket.voucher.model.Voucher;
import com.moldy.moldymarket.voucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherService {

    private final VoucherRepository voucherRepository;

    @Transactional
    public VoucherResponse createVoucher(CreateVoucherRequest request) {
        String code = request.getCode().trim().toUpperCase();
        
        if (voucherRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.VOUCHER_CODE_ALREADY_EXISTS);
        }

        Voucher voucher = new Voucher();
        voucher.setCode(code);
        voucher.setDiscountType(request.getDiscountType());
        voucher.setDiscountValue(request.getDiscountValue());
        voucher.setRemainingQuantity(request.getRemainingQuantity());
        voucher.setExpiredAt(request.getExpiredAt());

        voucherRepository.save(voucher);
        log.info("Voucher created: {}", code);
        return new VoucherResponse(voucher);
    }

    public VoucherCheckResponse checkVoucher(String code) {
        Voucher voucher = voucherRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        
        boolean valid = !voucher.getExpiredAt().isBefore(Instant.now())
                && voucher.getRemainingQuantity() > 0;
        
        log.debug("Voucher {} check: valid={}", code, valid);
        return new VoucherCheckResponse(voucher, valid);
    }

   
    public List<VoucherResponse> getAllVouchers() {
        return voucherRepository.findAll().stream()
                .map(VoucherResponse::new)
                .collect(Collectors.toList());
    }

   
    public VoucherResponse getVoucherByCode(String code) {
        Voucher voucher = voucherRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        return new VoucherResponse(voucher);
    }

   
    public Voucher validateAndGet(String code) {
        String normalizedCode = code.trim().toUpperCase();
        Voucher voucher = voucherRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        
        if (voucher.getExpiredAt().isBefore(Instant.now())) {
            log.warn("Voucher {} is expired", normalizedCode);
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        }
        
        if (voucher.getRemainingQuantity() <= 0) {
            log.warn("Voucher {} is out of stock", normalizedCode);
            throw new AppException(ErrorCode.VOUCHER_OUT_OF_STOCK);
        }
        
        log.info("Voucher {} validated for use", normalizedCode);
        return voucher;
    }

 
    @Transactional
    public void decreaseQuantity(Voucher voucher) {
        try {
            voucher.setRemainingQuantity(voucher.getRemainingQuantity() - 1);
            voucherRepository.save(voucher);
            log.info("Voucher {} quantity decreased to {}", voucher.getCode(), voucher.getRemainingQuantity());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic locking failure for voucher {}: concurrent modification detected", voucher.getCode(), e);
            throw new AppException(ErrorCode.VOUCHER_OUT_OF_STOCK);
        }
    }
}
