package com.moldy.moldymarket.wallet.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.wallet.payout.PayoutResult;
import com.moldy.moldymarket.wallet.payout.PayoutService;
import com.moldy.moldymarket.wallet.config.WithdrawalAutoApprovalProperties;
import com.moldy.moldymarket.wallet.dto.request.WithdrawalRequest;
import com.moldy.moldymarket.wallet.dto.response.WithdrawalResponse;
import com.moldy.moldymarket.wallet.entity.Wallet;
import com.moldy.moldymarket.wallet.entity.WalletTransaction;
import com.moldy.moldymarket.wallet.entity.Withdrawal;
import com.moldy.moldymarket.wallet.event.WithdrawalChangedEvent;
import com.moldy.moldymarket.wallet.enums.EscrowStatus;
import com.moldy.moldymarket.wallet.enums.TransactionType;
import com.moldy.moldymarket.wallet.enums.WithdrawalStatus;
import com.moldy.moldymarket.wallet.mapper.WithdrawalMapper;
import com.moldy.moldymarket.wallet.repository.EscrowHoldRepository;
import com.moldy.moldymarket.wallet.repository.WalletRepository;
import com.moldy.moldymarket.wallet.repository.WalletTransactionRepository;
import com.moldy.moldymarket.wallet.repository.WithdrawalRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WithdrawalService {
    WalletRepository walletRepository;
    WithdrawalRepository withdrawalRepository;
    WalletTransactionRepository walletTransactionRepository;
    EscrowHoldRepository escrowHoldRepository;
    WithdrawalMapper withdrawalMapper;
    WithdrawalAutoApprovalProperties autoApprovalProperties;
    PayoutService payoutService;
    ApplicationEventPublisher events;
    CurrentUser currentUser;


    @Transactional
    public WithdrawalResponse requestWithdrawal(WithdrawalRequest request){
        UUID callerId = currentUser.findCurrentUser().getId();
        if (request.userId() != null && !request.userId().equals(callerId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        //check if amount is smaller than zero?
        if(request.amount().compareTo(BigDecimal.ZERO) <= 0)
            throw new AppException(ErrorCode.WALLET_INVALID_AMOUNT);
        //identify idempotency key to avoid spam request from users, if key is not found, make a request
        Withdrawal withdrawal = withdrawalRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(existing -> {
                    Wallet callerWallet = walletRepository.findByUserId(callerId)
                            .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
                    if (!existing.getWalletId().equals(callerWallet.getId())) {
                        throw new AppException(ErrorCode.FORBIDDEN);
                    }
                    return existing;
                })
                .orElseGet(() -> doRequestWithdrawal(callerId, request));

        //check request for auto-approval
        if(withdrawal.getStatus() == WithdrawalStatus.REQUESTED && checkForAutoApproval(withdrawal)){
            withdrawal = autoProcess(withdrawal);
        }
        return withdrawalMapper.toWithdrawalResponse(withdrawal);
    }

    @Transactional
    public List<WithdrawalResponse> getMyWithdrawals() {
        UUID callerId = currentUser.findCurrentUser().getId();
        return walletRepository.findByUserId(callerId)
                .map(wallet -> withdrawalRepository.findByWalletIdOrderByRequestedAtDesc(wallet.getId())
                        .stream()
                        .map(withdrawalMapper::toWithdrawalResponse)
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional
    public WithdrawalResponse cancelMyWithdrawal(Long withdrawalId) {
        UUID callerId = currentUser.findCurrentUser().getId();
        Wallet callerWallet = walletRepository.findByUserId(callerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_EXISTED));
        if (!withdrawal.getWalletId().equals(callerWallet.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        requireStatus(withdrawal, WithdrawalStatus.REQUESTED);
        reverseHold(withdrawal);
        withdrawal.setStatus(WithdrawalStatus.CANCELLED);
        withdrawal.setProcessedAt(Instant.now());
        Withdrawal saved = withdrawalRepository.save(withdrawal);
        events.publishEvent(new WithdrawalChangedEvent(
                saved.getId(), callerId, WithdrawalStatus.CANCELLED));
        return withdrawalMapper.toWithdrawalResponse(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public List<WithdrawalResponse> getAllWithdrawalsForAdmin() {
        return withdrawalRepository.findAllByOrderByRequestedAtDesc()
                .stream()
                .map(withdrawalMapper::toWithdrawalResponse)
                .toList();
    }

    private Withdrawal doRequestWithdrawal(UUID userId, WithdrawalRequest request){
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));

        //check if the balance is smaller than request amount
        if(wallet.getAvailableBalance().compareTo(request.amount()) < 0){
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        //if balance is sufficient, let's subtract amount, then add to pending withdrawal balance
        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(request.amount()));
        wallet.setPendingWithdrawBalance(wallet.getPendingWithdrawBalance().add(request.amount()));
        walletRepository.save(wallet);

        //save withdrawal request
        Withdrawal withdrawal = withdrawalMapper.toWithdrawal(request);
        withdrawal.setWalletId(wallet.getId());
        withdrawal.setStatus(WithdrawalStatus.REQUESTED);
        withdrawalRepository.save(withdrawal);
        events.publishEvent(new WithdrawalChangedEvent(
                withdrawal.getId(), wallet.getUserId(), WithdrawalStatus.REQUESTED));

        //create wallet transaction
        var walletTransaction = WalletTransaction.create(wallet, TransactionType.WITHDRAWAL_HOLD, request.amount(), "WITHDRAWAL", String.valueOf(withdrawal.getId()), request.idempotencyKey(), "Withdrawal is successful!");
        walletTransactionRepository.save(walletTransaction);

        return withdrawal;
    }

    private boolean checkForAutoApproval(Withdrawal withdrawal){
        if(!autoApprovalProperties.isEnabled()) return false;

        //withdrawal amount is exceeded max withdrawal threshold
        if(withdrawal.getAmount().compareTo(autoApprovalProperties.getMaxAmount()) > 0){
            log.info("Withdrawal #{} exceeds auto-approval threshold -> manual withdrawal", withdrawal.getId());
            return false;
        }

        //If it's the first time withdrawal
        boolean knowBankAccount = withdrawalRepository.existsByWalletIdAndBankAccountNumberAndStatus(withdrawal.getWalletId(), withdrawal.getBankAccountNumber(), WithdrawalStatus.COMPLETED);
        if(!knowBankAccount){
            log.info("Withdrawal #{} uses a new bank account -> manual withdrawal", withdrawal.getId());
            return false;
        }

        //If user is in disputed situation
        boolean hasOpenDispute = escrowHoldRepository.existsBySellerWalletIdAndStatus(
                withdrawal.getWalletId(), EscrowStatus.DISPUTED);

        if(hasOpenDispute){
            log.info("Withdrawal #{} in wallet being disputed -> manual withdrawal", withdrawal.getId());
            return false;
        }

        //if users withdraw over threshold within 24h
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        BigDecimal recentTotal = withdrawalRepository.sumRecentWithdrawals(withdrawal.getWalletId(), since);
        if(recentTotal.compareTo(autoApprovalProperties.getMaxDailyTotal()) > 0){
            log.info("Withdrawal #{} requests have exceeded total daily amount within 24h -> manual withdrawal", withdrawal.getId());
            return false;
        }
        return true;
    }

    private Withdrawal autoProcess(Withdrawal withdrawal){
        withdrawal.setStatus(WithdrawalStatus.PROCESSING);
        withdrawal = withdrawalRepository.save(withdrawal);

        PayoutResult result = payoutService.initiatePayout(withdrawal);
        return switch (result){
            case SUCCESS -> doComplete(withdrawal);
            case FAILED -> doFail(withdrawal, "Payment gateway refused banking");
            case PENDING -> withdrawal; //wait webhook calling complete()/fail()
        };

    }
    private Withdrawal doComplete(Withdrawal withdrawal){
        Wallet wallet = walletRepository.findByIdForUpdate(withdrawal.getWalletId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));

        wallet.setPendingWithdrawBalance(wallet.getPendingWithdrawBalance().subtract(withdrawal.getAmount()));
        walletRepository.save(wallet);

        withdrawal.setStatus(WithdrawalStatus.COMPLETED);
        withdrawal.setProcessedAt(Instant.now());
        withdrawalRepository.save(withdrawal);
        events.publishEvent(new WithdrawalChangedEvent(
                withdrawal.getId(), wallet.getUserId(), WithdrawalStatus.COMPLETED));

        var walletTransaction = WalletTransaction.create(wallet, TransactionType.WITHDRAWAL_DONE, withdrawal.getAmount(), "WITHDRAWAL", String.valueOf(withdrawal.getId()), "wd-done: " + withdrawal.getId(), "Withdrawal is successful! " + withdrawal.getId());
        walletTransactionRepository.save(walletTransaction);

        return withdrawal;
    }
    private Withdrawal doFail(Withdrawal withdrawal, String reason){
        reverseHold(withdrawal);
        withdrawal.setStatus(WithdrawalStatus.FAILED);
        withdrawal.setRejectionReason(reason);
        withdrawal.setProcessedAt(Instant.now());
        Withdrawal saved = withdrawalRepository.save(withdrawal);
        Wallet wallet = walletRepository.findById(withdrawal.getWalletId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        events.publishEvent(new WithdrawalChangedEvent(
                saved.getId(), wallet.getUserId(), WithdrawalStatus.FAILED));
        return saved;
    }

    private void reverseHold(Withdrawal withdrawal){
        Wallet wallet = walletRepository.findByIdForUpdate(withdrawal.getWalletId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        wallet.setPendingWithdrawBalance(wallet.getPendingWithdrawBalance().subtract(withdrawal.getAmount()));
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(withdrawal.getAmount()));
        walletRepository.save(wallet);

        walletTransactionRepository.save(WalletTransaction.create(wallet, TransactionType.WITHDRAWAL_REVERSE, withdrawal.getAmount(),
                "WITHDRAWAL", String.valueOf(withdrawal.getId()), "wd-reverse:" + withdrawal.getId(),
                "Return money due to request #" + withdrawal.getId() + " unsuccessful"));
    }

    //manual approval from admin
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public WithdrawalResponse approve(Long withdrawalId){
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_EXISTED));
        requireStatus(withdrawal, WithdrawalStatus.REQUESTED);
        withdrawal.setStatus(WithdrawalStatus.PROCESSING);
        return withdrawalMapper.toWithdrawalResponse(withdrawalRepository.save(withdrawal));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public WithdrawalResponse complete(Long withdrawalId){
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_EXISTED));
        requireStatus(withdrawal, WithdrawalStatus.PROCESSING);
        return withdrawalMapper.toWithdrawalResponse(doComplete(withdrawal));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public WithdrawalResponse reject(Long withdrawalId, String reason){
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_EXISTED));
        requireStatus(withdrawal, WithdrawalStatus.REQUESTED);
        reverseHold(withdrawal);
        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setRejectionReason(reason);
        withdrawal.setProcessedAt(Instant.now());
        Withdrawal saved = withdrawalRepository.save(withdrawal);
        Wallet wallet = walletRepository.findById(withdrawal.getWalletId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        events.publishEvent(new WithdrawalChangedEvent(
                saved.getId(), wallet.getUserId(), WithdrawalStatus.REJECTED));
        return withdrawalMapper.toWithdrawalResponse(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public WithdrawalResponse fail(Long withdrawalId, String reason){
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_EXISTED));
        requireStatus(withdrawal, WithdrawalStatus.PROCESSING);
        return withdrawalMapper.toWithdrawalResponse(doFail(withdrawal, reason));
    }

    private void requireStatus(Withdrawal withdrawal, WithdrawalStatus status){
        if(withdrawal.getStatus() != status){
            throw new AppException(ErrorCode.INVALID_WITHDRAWAL_STATE);
        }
    }
}
