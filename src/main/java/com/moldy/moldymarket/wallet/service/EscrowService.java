package com.moldy.moldymarket.wallet.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.wallet.dto.request.EscrowHoldRequest;
import com.moldy.moldymarket.wallet.dto.response.EscrowHoldResponse;
import com.moldy.moldymarket.wallet.entity.EscrowHold;
import com.moldy.moldymarket.wallet.entity.Wallet;
import com.moldy.moldymarket.wallet.entity.WalletTransaction;
import com.moldy.moldymarket.wallet.enums.DisputeOutcome;
import com.moldy.moldymarket.wallet.enums.EscrowStatus;
import com.moldy.moldymarket.wallet.enums.TransactionType;
import com.moldy.moldymarket.wallet.mapper.EscrowMapper;
import com.moldy.moldymarket.wallet.repository.EscrowHoldRepository;
import com.moldy.moldymarket.wallet.repository.WalletRepository;
import com.moldy.moldymarket.wallet.repository.WalletTransactionRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EscrowService {
    WalletRepository walletRepository;
    EscrowHoldRepository escrowHoldRepository;
    WalletTransactionRepository transactionRepository;
    EscrowMapper escrowMapper;

    @Transactional
    public EscrowHoldResponse hold(EscrowHoldRequest request) {
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.WALLET_INVALID_AMOUNT);
        }

        return escrowMapper.toEscrowHoldResponse(escrowHoldRepository.findByOrderId(request.orderId())
                .orElseGet(() -> doHold(request)));
    }

    private EscrowHold doHold(EscrowHoldRequest request) {
        Wallet buyerWallet = walletRepository.findByUserIdForUpdate(request.buyerId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        Wallet sellerWallet = walletRepository.findByUserId(request.sellerId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));

        if (buyerWallet.getAvailableBalance().compareTo(request.amount()) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        buyerWallet.setAvailableBalance(buyerWallet.getAvailableBalance().subtract(request.amount()));
        buyerWallet.setHeldBalance(buyerWallet.getHeldBalance().add(request.amount()));
        walletRepository.save(buyerWallet);

        transactionRepository.save(WalletTransaction.create(
                buyerWallet, TransactionType.HOLD, request.amount(), "ORDER", request.orderId(),
                "hold:" + request.orderId(), "Holding for order " + request.orderId()
        ));

        EscrowHold hold = new EscrowHold();
        hold.setOrderId(request.orderId());
        hold.setBuyerWalletId(buyerWallet.getId());
        hold.setSellerWalletId(sellerWallet.getId());
        hold.setAmount(request.amount());
        hold.setFeeAmount(request.fee());
        hold.setStatus(EscrowStatus.HELD);

        return escrowHoldRepository.save(hold);
    }

    @Transactional
    public EscrowHoldResponse capture(String orderId) {
        EscrowHold hold = getHoldForUpdate(orderId);
        requireStatus(hold, EscrowStatus.HELD);

        applyCapture(hold);
        hold.setStatus(EscrowStatus.CAPTURED);
        hold.setResolvedAt(java.time.Instant.now());
        return escrowMapper.toEscrowHoldResponse(escrowHoldRepository.save(hold));
    }

    @Transactional
    public EscrowHoldResponse release(String orderId) {
        EscrowHold hold = getHoldForUpdate(orderId);
        requireStatus(hold, EscrowStatus.HELD);

        applyRelease(hold);
        hold.setStatus(EscrowStatus.RELEASED);
        hold.setResolvedAt(java.time.Instant.now());
        return escrowMapper.toEscrowHoldResponse(escrowHoldRepository.save(hold));
    }

    public EscrowHold openDispute(String orderId) {
        EscrowHold hold = getHoldForUpdate(orderId);
        requireStatus(hold, EscrowStatus.HELD);

        hold.setStatus(EscrowStatus.DISPUTED);
        return escrowHoldRepository.save(hold);
    }

    public EscrowHold resolveDispute(String orderId, DisputeOutcome outcome) {
        EscrowHold hold = getHoldForUpdate(orderId);
        requireStatus(hold, EscrowStatus.DISPUTED);

        hold.setStatus(EscrowStatus.HELD);

        if (outcome == DisputeOutcome.PAY_SELLER) {
            applyCapture(hold);
            hold.setStatus(EscrowStatus.CAPTURED);
        } else {
            applyRelease(hold);
            hold.setStatus(EscrowStatus.RELEASED);
        }

        hold.setResolvedAt(java.time.Instant.now());
        return escrowHoldRepository.save(hold);
    }


    private EscrowHold getHoldForUpdate(String orderId) {
        return escrowHoldRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ESCROW_NOT_FOUND));
    }

    private void requireStatus(EscrowHold hold, EscrowStatus expected) {
        if (hold.getStatus() != expected) {
            throw new AppException(ErrorCode.INVALID_ESCROW_STATE);
        }
    }

    private void applyCapture(EscrowHold hold) {
        Long firstId = Math.min(hold.getBuyerWalletId(), hold.getSellerWalletId());
        Long secondId = Math.max(hold.getBuyerWalletId(), hold.getSellerWalletId());

        Wallet firstLocked = walletRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        Wallet secondLocked = walletRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));

        Wallet buyerWallet = firstId.equals(hold.getBuyerWalletId()) ? firstLocked : secondLocked;
        Wallet sellerWallet = firstId.equals(hold.getSellerWalletId()) ? firstLocked : secondLocked;

        BigDecimal amount = hold.getAmount();
        BigDecimal fee = hold.getFeeAmount();
        BigDecimal netToSeller = amount.subtract(fee);

        buyerWallet.setHeldBalance(buyerWallet.getHeldBalance().subtract(amount));
        sellerWallet.setAvailableBalance(sellerWallet.getAvailableBalance().add(netToSeller));

        walletRepository.save(buyerWallet);
        walletRepository.save(sellerWallet);

        transactionRepository.save(WalletTransaction.create(
                buyerWallet, TransactionType.CAPTURE, amount, "ORDER", hold.getOrderId(),
                "capture-buyer:" + hold.getOrderId(), "Paid for seller"
        ));

        transactionRepository.save(WalletTransaction.create(
                sellerWallet, TransactionType.CAPTURE, netToSeller, "ORDER", hold.getOrderId(),
                "capture-seller:" + hold.getOrderId(), "Received from order " + hold.getOrderId()
        ));

        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            transactionRepository.save(WalletTransaction.create(
                    sellerWallet, TransactionType.FEE, fee, "ORDER", hold.getOrderId(),
                    "fee:" + hold.getOrderId(), "Fee platform"
            ));
        }

        hold.setCapturedAmount(amount);
    }

    private void applyRelease(EscrowHold hold) {
        BigDecimal amount = hold.getAmount();

        Wallet buyerWallet = walletRepository.findByIdForUpdate(hold.getBuyerWalletId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));

        buyerWallet.setHeldBalance(buyerWallet.getHeldBalance().subtract(amount));
        buyerWallet.setAvailableBalance(buyerWallet.getAvailableBalance().add(amount));
        walletRepository.save(buyerWallet);

        transactionRepository.save(WalletTransaction.create(
                buyerWallet, TransactionType.RELEASE, amount, "ORDER", hold.getOrderId(),
                "release:" + hold.getOrderId(), "Return money for order " + hold.getOrderId()
        ));

        hold.setReleasedAmount(amount);
    }
}
