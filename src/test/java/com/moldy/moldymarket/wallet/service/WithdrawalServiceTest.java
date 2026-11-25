package com.moldy.moldymarket.wallet.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.wallet.config.WithdrawalAutoApprovalProperties;
import com.moldy.moldymarket.wallet.dto.request.WithdrawalRequest;
import com.moldy.moldymarket.wallet.dto.response.WithdrawalResponse;
import com.moldy.moldymarket.wallet.entity.Wallet;
import com.moldy.moldymarket.wallet.entity.Withdrawal;
import com.moldy.moldymarket.wallet.enums.WithdrawalStatus;
import com.moldy.moldymarket.wallet.mapper.WithdrawalMapper;
import com.moldy.moldymarket.wallet.payout.PayoutService;
import com.moldy.moldymarket.wallet.repository.EscrowHoldRepository;
import com.moldy.moldymarket.wallet.repository.WalletRepository;
import com.moldy.moldymarket.wallet.repository.WalletTransactionRepository;
import com.moldy.moldymarket.wallet.repository.WithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock WithdrawalRepository withdrawalRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock EscrowHoldRepository escrowHoldRepository;
    @Mock WithdrawalMapper withdrawalMapper;
    @Mock WithdrawalAutoApprovalProperties autoApprovalProperties;
    @Mock PayoutService payoutService;
    @Mock ApplicationEventPublisher events;
    @Mock CurrentUser currentUser;

    @InjectMocks WithdrawalService withdrawalService;

    UUID userId = UUID.randomUUID();
    User user;
    Wallet wallet;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(userId);
        wallet = new Wallet();
        wallet.setId(5L);
        wallet.setUserId(userId);
        wallet.setAvailableBalance(BigDecimal.valueOf(500000));
        wallet.setPendingWithdrawBalance(BigDecimal.ZERO);
    }

    @Test
    void rejectsWithdrawalForAnotherUsersWallet() {
        when(currentUser.findCurrentUser()).thenReturn(user);
        var request = new WithdrawalRequest(
                UUID.randomUUID(), BigDecimal.valueOf(100000),
                "VCB", "0123456789", "NGUYEN VAN A", "idem-1"
        );

        assertThatThrownBy(() -> withdrawalService.requestWithdrawal(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        verifyNoInteractions(walletRepository, withdrawalRepository);
    }

    @Test
    void ownerCanCancelRequestedWithdrawalAndRestoreAvailableBalance() {
        wallet.setAvailableBalance(BigDecimal.valueOf(300000));
        wallet.setPendingWithdrawBalance(BigDecimal.valueOf(200000));
        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setId(99L);
        withdrawal.setWalletId(wallet.getId());
        withdrawal.setAmount(BigDecimal.valueOf(200000));
        withdrawal.setStatus(WithdrawalStatus.REQUESTED);

        when(currentUser.findCurrentUser()).thenReturn(user);
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(withdrawalRepository.findById(99L)).thenReturn(Optional.of(withdrawal));
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(withdrawalRepository.save(withdrawal)).thenReturn(withdrawal);
        when(withdrawalMapper.toWithdrawalResponse(withdrawal)).thenReturn(mock(WithdrawalResponse.class));

        withdrawalService.cancelMyWithdrawal(99L);

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.CANCELLED);
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(500000));
        assertThat(wallet.getPendingWithdrawBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(walletTransactionRepository).save(any());
    }

    @Test
    void cannotCancelAnotherUsersWithdrawal() {
        Withdrawal otherWithdrawal = new Withdrawal();
        otherWithdrawal.setId(100L);
        otherWithdrawal.setWalletId(999L);
        otherWithdrawal.setStatus(WithdrawalStatus.REQUESTED);

        when(currentUser.findCurrentUser()).thenReturn(user);
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(withdrawalRepository.findById(100L)).thenReturn(Optional.of(otherWithdrawal));

        assertThatThrownBy(() -> withdrawalService.cancelMyWithdrawal(100L))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }
}
