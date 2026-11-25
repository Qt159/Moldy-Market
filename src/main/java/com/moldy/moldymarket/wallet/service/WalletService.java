package com.moldy.moldymarket.wallet.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.wallet.dto.response.WalletResponse;
import com.moldy.moldymarket.wallet.dto.response.WalletTransactionResponse;
import com.moldy.moldymarket.wallet.entity.Wallet;
import com.moldy.moldymarket.wallet.entity.WalletTransaction;
import com.moldy.moldymarket.wallet.enums.TransactionType;
import com.moldy.moldymarket.wallet.mapper.WalletMapper;
import com.moldy.moldymarket.wallet.repository.WalletRepository;
import com.moldy.moldymarket.wallet.repository.WalletTransactionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WalletService {
    WalletRepository walletRepository;
    WalletTransactionRepository walletTransactionRepository;
    WalletMapper walletMapper;
    CurrentUser currentUser;

    //Whenever users are created, their wallet is also created
    public void createWalletForUser(UUID userId){
        if(walletRepository.findByUserId(userId).isPresent())
            throw new AppException(ErrorCode.WALLET_EXISTED);

        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        try{
            walletMapper.toWalletResponse(walletRepository.save(wallet));
        }catch (DataIntegrityViolationException ex){
            throw new AppException(ErrorCode.WALLET_EXISTED);
        }
    }

    @Transactional
    public WalletResponse getMyWallet() {
        UUID userId = currentUser.findCurrentUser().getId();
        return walletMapper.toWalletResponse(getOrCreateWallet(userId));
    }

    @Transactional
    public WalletResponse getWalletByUserId(UUID userId){
        UUID callerId = currentUser.findCurrentUser().getId();
        if (!callerId.equals(userId) && !currentUser.hasRole("ADMIN")) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        Wallet wallet = callerId.equals(userId)
                ? getOrCreateWallet(userId)
                : walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        return walletMapper.toWalletResponse(wallet);
    }

    @Transactional
    public List<WalletTransactionResponse> getMyTransactions() {
        UUID userId = currentUser.findCurrentUser().getId();
        Wallet wallet = getOrCreateWallet(userId);
        return walletTransactionRepository.findAllByWalletIdOrderByCreatedAtDesc(wallet.getId())
                .stream()
                .map(WalletTransactionResponse::from)
                .toList();
    }

    @Transactional
    public void creditDisputeRefund(UUID buyerId, UUID orderId, String orderCode, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String idempotencyKey = "dispute-refund:" + orderId;
        if (walletTransactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }
        getOrCreateWallet(buyerId);
        Wallet wallet = walletRepository.findByUserIdForUpdate(buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
        walletRepository.save(wallet);

        String label = orderCode != null && !orderCode.isBlank() ? orderCode : orderId.toString();
        WalletTransaction tx = WalletTransaction.create(
                wallet,
                TransactionType.REFUND,
                amount,
                "DISPUTE_REFUND",
                orderId.toString(),
                idempotencyKey,
                "Dispute refund for order " + label
        );
        walletTransactionRepository.save(tx);
    }

    @Transactional
    public void creditSellerPayout(UUID sellerId, UUID orderId, String orderCode, BigDecimal amount) {
        if (sellerId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String idempotencyKey = "order-payout:" + orderId;

        if (walletTransactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        getOrCreateWallet(sellerId);
        Wallet wallet = walletRepository.findByUserIdForUpdate(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));

        wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
        walletRepository.save(wallet);

        String label = orderCode != null && !orderCode.isBlank() ? orderCode : orderId.toString();
        WalletTransaction tx = WalletTransaction.create(
                wallet,
                TransactionType.CAPTURE,
                amount,
                "ORDER_PAYOUT",
                orderId.toString(),
                idempotencyKey,
                "Seller payout for order " + label
        );
        walletTransactionRepository.save(tx);
    }

    private Wallet getOrCreateWallet(UUID userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            Wallet wallet = new Wallet();
            wallet.setUserId(userId);
            try {
                return walletRepository.saveAndFlush(wallet);
            } catch (DataIntegrityViolationException ex) {
                return walletRepository.findByUserId(userId)
                        .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
            }
        });
    }
}
