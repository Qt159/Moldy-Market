package com.moldy.moldymarket.voucher.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.voucher.dto.CreateVoucherRequest;
import com.moldy.moldymarket.voucher.dto.VoucherCheckResponse;
import com.moldy.moldymarket.voucher.dto.VoucherResponse;
import com.moldy.moldymarket.voucher.model.DiscountType;
import com.moldy.moldymarket.voucher.model.Voucher;
import com.moldy.moldymarket.voucher.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @InjectMocks
    private VoucherService voucherService;

    private Voucher sampleVoucher;
    private UUID voucherId;

    @BeforeEach
    void setUp() {
        voucherId = UUID.randomUUID();
        sampleVoucher = new Voucher();
        sampleVoucher.setId(voucherId);
        sampleVoucher.setCode("SUMMER2026");
        sampleVoucher.setDiscountType(DiscountType.PERCENT);
        sampleVoucher.setDiscountValue(BigDecimal.valueOf(15));
        sampleVoucher.setRemainingQuantity(100);
        sampleVoucher.setExpiredAt(Instant.now().plus(30, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("should create voucher successfully with normalized uppercase code")
    void should_createVoucher_when_valid() {
        // Arrange
        CreateVoucherRequest request = new CreateVoucherRequest(
                "  summer2026  ", DiscountType.PERCENT, BigDecimal.valueOf(15), 100, Instant.now().plus(30, ChronoUnit.DAYS)
        );

        when(voucherRepository.existsByCode("SUMMER2026")).thenReturn(false);
        when(voucherRepository.save(any(Voucher.class))).thenReturn(sampleVoucher);

        // Act
        VoucherResponse response = voucherService.createVoucher(request);

        // Assert
        assertThat(response).isNotNull();
        verify(voucherRepository).save(any(Voucher.class));
    }

    @Test
    @DisplayName("should throw VOUCHER_CODE_ALREADY_EXISTS when code exists")
    void should_throwVoucherCodeAlreadyExists_when_duplicate() {
        // Arrange
        CreateVoucherRequest request = new CreateVoucherRequest(
                "SUMMER2026", DiscountType.FIXED, BigDecimal.valueOf(50000), 50, Instant.now().plus(10, ChronoUnit.DAYS)
        );

        when(voucherRepository.existsByCode("SUMMER2026")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> voucherService.createVoucher(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOUCHER_CODE_ALREADY_EXISTS);

        verify(voucherRepository, never()).save(any());
    }

    @Test
    @DisplayName("should return valid=true when voucher is active and quantity > 0")
    void should_checkVoucher_when_valid() {
        // Arrange
        when(voucherRepository.findByCode("SUMMER2026")).thenReturn(Optional.of(sampleVoucher));

        // Act
        VoucherCheckResponse response = voucherService.checkVoucher("  summer2026  ");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.isValid()).isTrue();
    }

    @Test
    @DisplayName("should return valid=false when voucher is expired")
    void should_checkVoucherAsInvalid_when_expired() {
        // Arrange
        sampleVoucher.setExpiredAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(voucherRepository.findByCode("SUMMER2026")).thenReturn(Optional.of(sampleVoucher));

        // Act
        VoucherCheckResponse response = voucherService.checkVoucher("SUMMER2026");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.isValid()).isFalse();
    }

    @Test
    @DisplayName("should throw VOUCHER_NOT_FOUND when voucher does not exist")
    void should_throwVoucherNotFound_when_codeDoesNotExist() {
        // Arrange
        when(voucherRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> voucherService.checkVoucher("UNKNOWN"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOUCHER_NOT_FOUND);
    }

    @Test
    @DisplayName("should validate and return voucher when it is valid for checkout")
    void should_validateAndGet_when_valid() {
        // Arrange
        when(voucherRepository.findByCode("SUMMER2026")).thenReturn(Optional.of(sampleVoucher));

        // Act
        Voucher voucher = voucherService.validateAndGet("summer2026");

        // Assert
        assertThat(voucher).isNotNull();
        assertThat(voucher.getCode()).isEqualTo("SUMMER2026");
    }

    @Test
    @DisplayName("should throw VOUCHER_EXPIRED when attempting to use expired voucher")
    void should_throwVoucherExpired_when_validatingExpiredVoucher() {
        // Arrange
        sampleVoucher.setExpiredAt(Instant.now().minus(2, ChronoUnit.HOURS));
        when(voucherRepository.findByCode("SUMMER2026")).thenReturn(Optional.of(sampleVoucher));

        // Act & Assert
        assertThatThrownBy(() -> voucherService.validateAndGet("SUMMER2026"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOUCHER_EXPIRED);
    }

    @Test
    @DisplayName("should throw VOUCHER_OUT_OF_STOCK when remaining quantity is 0")
    void should_throwVoucherOutOfStock_when_quantityZero() {
        // Arrange
        sampleVoucher.setRemainingQuantity(0);
        when(voucherRepository.findByCode("SUMMER2026")).thenReturn(Optional.of(sampleVoucher));

        // Act & Assert
        assertThatThrownBy(() -> voucherService.validateAndGet("SUMMER2026"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOUCHER_OUT_OF_STOCK);
    }

    @Test
    @DisplayName("should decrease voucher quantity by 1 on use")
    void should_decreaseQuantity_when_used() {
        // Arrange
        sampleVoucher.setRemainingQuantity(10);

        // Act
        voucherService.decreaseQuantity(sampleVoucher);

        // Assert
        assertThat(sampleVoucher.getRemainingQuantity()).isEqualTo(9);
        verify(voucherRepository).save(sampleVoucher);
    }

    @Test
    @DisplayName("should catch ObjectOptimisticLockingFailureException and throw VOUCHER_OUT_OF_STOCK on concurrent race")
    void should_throwVoucherOutOfStock_when_optimisticLockingFails() {
        // Arrange
        when(voucherRepository.save(sampleVoucher))
                .thenThrow(new ObjectOptimisticLockingFailureException(Voucher.class, voucherId));

        // Act & Assert
        assertThatThrownBy(() -> voucherService.decreaseQuantity(sampleVoucher))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VOUCHER_OUT_OF_STOCK);
    }
}
