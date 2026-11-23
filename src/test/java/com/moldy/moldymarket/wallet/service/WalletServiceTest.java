package com.moldy.moldymarket.wallet.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.wallet.dto.response.WalletResponse;
import com.moldy.moldymarket.wallet.entity.Wallet;
import com.moldy.moldymarket.wallet.entity.WalletTransaction;
import com.moldy.moldymarket.wallet.enums.TransactionType;
import com.moldy.moldymarket.wallet.mapper.WalletMapper;
import com.moldy.moldymarket.wallet.repository.WalletRepository;
import com.moldy.moldymarket.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock WalletMapper walletMapper;
    @Mock CurrentUser currentUser;

    @InjectMocks WalletService walletService;

    UUID userId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    User user;
    Wallet wallet;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(userId);
        wallet = new Wallet();
        wallet.setId(10L);
        wallet.setUserId(userId);
        wallet.setAvailableBalance(BigDecimal.valueOf(200000));
    }

    @Test
    void nonAdminCannotReadAnotherUsersWallet() {
        when(currentUser.findCurrentUser()).thenReturn(user);
        when(currentUser.hasRole("ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> walletService.getWalletByUserId(otherUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        verifyNoInteractions(walletRepository);
    }

    @Test
    void adminCanReadAnotherUsersWallet() {
        Wallet otherWallet = new Wallet();
        otherWallet.setId(11L);
        otherWallet.setUserId(otherUserId);
        when(currentUser.findCurrentUser()).thenReturn(user);
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        when(walletRepository.findByUserId(otherUserId)).thenReturn(Optional.of(otherWallet));
        when(walletMapper.toWalletResponse(otherWallet)).thenReturn(mock(WalletResponse.class));

        assertThat(walletService.getWalletByUserId(otherUserId)).isNotNull();
    }

    @Test
    void creditDisputeRefundAddsBalanceAndRecordsRefundTransactionIdempotently() {
        UUID orderId = UUID.randomUUID();
        when(walletTransactionRepository.existsByIdempotencyKey("dispute-refund:" + orderId)).thenReturn(false);
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));

        walletService.creditDisputeRefund(userId, orderId, "ORD-REF01", BigDecimal.valueOf(150000));

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(350000));
        verify(walletRepository).save(wallet);
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionType()).isEqualTo(TransactionType.REFUND);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(150000));
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("dispute-refund:" + orderId);
    }

    @Test
    void creditDisputeRefundSkipsDuplicateIdempotencyKey() {
        UUID orderId = UUID.randomUUID();
        when(walletTransactionRepository.existsByIdempotencyKey("dispute-refund:" + orderId)).thenReturn(true);

        walletService.creditDisputeRefund(userId, orderId, "ORD-REF01", BigDecimal.valueOf(150000));

        verifyNoInteractions(walletRepository);
        verify(walletTransactionRepository, never()).save(any());
    }
}
