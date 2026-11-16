package com.moldy.moldymarket.wallet.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import com.moldy.moldymarket.wallet.dto.request.WithdrawalRequest;
import com.moldy.moldymarket.wallet.dto.response.WithdrawalResponse;
import com.moldy.moldymarket.wallet.dto.response.AdminWithdrawalResponse;
import com.moldy.moldymarket.wallet.entity.Wallet;
import com.moldy.moldymarket.wallet.entity.WalletTransaction;
import com.moldy.moldymarket.wallet.entity.Withdrawal;
import com.moldy.moldymarket.wallet.enums.TransactionType;
import com.moldy.moldymarket.wallet.enums.WithdrawalStatus;
import com.moldy.moldymarket.wallet.mapper.WithdrawalMapper;
import com.moldy.moldymarket.wallet.repository.WalletRepository;
import com.moldy.moldymarket.wallet.repository.WalletTransactionRepository;
import com.moldy.moldymarket.wallet.repository.WithdrawalRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WithdrawalService {
    WalletRepository walletRepository;
    WithdrawalRepository withdrawalRepository;
    WalletTransactionRepository walletTransactionRepository;
    WithdrawalMapper withdrawalMapper;
    CurrentUser currentUser;


    @Transactional
    public WithdrawalResponse requestWithdrawal(WithdrawalRequest request){
        var userId = currentUser.findCurrentUser().getId();
        if (request.userId() != null && !userId.equals(request.userId())) {
            throw new AppException(ErrorCode.WALLET_ACCESS_DENIED);
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.WALLET_INVALID_AMOUNT);
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        String key = "wd:" + wallet.getId() + ":" + request.idempotencyKey();
        var previous = withdrawalRepository.findByIdempotencyKey(key)
                .or(() -> withdrawalRepository.findByIdempotencyKey(request.idempotencyKey())
                        .filter(withdrawal -> wallet.getId().equals(withdrawal.getWalletId())));
        if (previous.isPresent()) {
            Withdrawal existing = previous.get();
            if (existing.getAmount().compareTo(request.amount()) != 0
                    || !existing.getBankName().equals(request.bankName())
                    || !existing.getBankAccountNumber().equals(request.bankAccountNumber())
                    || !existing.getBankAccountHolder().equals(request.bankAccountHolder())) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Withdrawal key already used for a different request");
            }
            return withdrawalMapper.toWithdrawalResponse(existing);
        }
        return withdrawalMapper.toWithdrawalResponse(doRequestWithdrawal(wallet, request, key));
    }

    private Withdrawal doRequestWithdrawal(Wallet wallet, WithdrawalRequest request, String key) {
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
        withdrawal.setIdempotencyKey(key);
        withdrawal.setStatus(WithdrawalStatus.REQUESTED);
        withdrawalRepository.save(withdrawal);

        //create wallet transaction
        var walletTransaction = WalletTransaction.create(wallet, TransactionType.WITHDRAWAL_HOLD, request.amount(), "WITHDRAWAL", String.valueOf(withdrawal.getId()), key, "Withdrawal requested");
        walletTransactionRepository.save(walletTransaction);

        return withdrawal;
    }

    private Withdrawal doComplete(Withdrawal withdrawal){
        Wallet wallet = walletRepository.findByIdForUpdate(withdrawal.getWalletId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));

        wallet.setPendingWithdrawBalance(wallet.getPendingWithdrawBalance().subtract(withdrawal.getAmount()));
        walletRepository.save(wallet);

        withdrawal.setStatus(WithdrawalStatus.COMPLETED);
        withdrawal.setProcessedAt(Instant.now());
        withdrawalRepository.save(withdrawal);

        var walletTransaction = WalletTransaction.create(wallet, TransactionType.WITHDRAWAL_DONE, withdrawal.getAmount(), "WITHDRAWAL", String.valueOf(withdrawal.getId()), "wd-done: " + withdrawal.getId(), "Withdrawal is successful! " + withdrawal.getId());
        walletTransactionRepository.save(walletTransaction);

        return withdrawal;
    }
    private Withdrawal doFail(Withdrawal withdrawal, String reason){
        reverseHold(withdrawal);
        withdrawal.setStatus(WithdrawalStatus.FAILED);
        withdrawal.setRejectionReason(reason);
        withdrawal.setProcessedAt(Instant.now());
        return withdrawalRepository.save(withdrawal);
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

    @IsAdmin
    public Slice<AdminWithdrawalResponse> list(WithdrawalStatus status, Pageable pageable) {
        return withdrawalRepository.findByStatusOrderByRequestedAtAsc(status, pageable)
                .map(withdrawalMapper::toAdminWithdrawalResponse);
    }

    @Transactional
    @IsAdmin
    public WithdrawalResponse approve(Long withdrawalId){
        Withdrawal withdrawal = withdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_EXISTED));
        requireStatus(withdrawal, WithdrawalStatus.REQUESTED);
        withdrawal.setStatus(WithdrawalStatus.PROCESSING);
        return withdrawalMapper.toWithdrawalResponse(withdrawalRepository.save(withdrawal));
    }
    @Transactional
    @IsAdmin
    public WithdrawalResponse complete(Long withdrawalId){
        Withdrawal withdrawal = withdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_EXISTED));
        requireStatus(withdrawal, WithdrawalStatus.PROCESSING);
        return withdrawalMapper.toWithdrawalResponse(doComplete(withdrawal));
    }
    @Transactional
    @IsAdmin
    public WithdrawalResponse reject(Long withdrawalId, String reason){
        Withdrawal withdrawal = withdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_EXISTED));
        requireStatus(withdrawal, WithdrawalStatus.REQUESTED);
        reverseHold(withdrawal);
        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setRejectionReason(reason);
        withdrawal.setProcessedAt(Instant.now());
        return withdrawalMapper.toWithdrawalResponse(withdrawalRepository.save(withdrawal));
    }

    @Transactional
    @IsAdmin
    public WithdrawalResponse fail(Long withdrawalId, String reason){
        Withdrawal withdrawal = withdrawalRepository.findByIdForUpdate(withdrawalId)
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
