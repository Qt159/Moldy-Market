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
import java.time.Instant;
import java.util.UUID;

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
    public void hold(EscrowHoldRequest request) {
        if (request.amount().signum() <= 0 || request.fee().signum() < 0) {
            throw new AppException(ErrorCode.INVALID_ORDER_AMOUNT);
        }

        if(escrowHoldRepository.existsByOrderId(request.orderId())) return;

        doHold(request);
    }

    private void doHold(EscrowHoldRequest request) {
        Wallet buyerWallet = walletRepository.findByUserId(request.buyerId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        Wallet sellerWallet = walletRepository.findByUserIdForUpdate(request.sellerId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));

        sellerWallet.setHeldBalance(request.amount());
        walletRepository.save(sellerWallet);

        transactionRepository.save(WalletTransaction.create(
                sellerWallet, TransactionType.HOLD, request.amount(), "ORDER", request.orderId().toString(),
                "hold:" + request.orderId(), "Holding for order " + request.orderId()
        ));

        EscrowHold hold = new EscrowHold();
        hold.setOrderId(request.orderId());
        hold.setBuyerWalletId(buyerWallet.getId());
        hold.setSellerWalletId(sellerWallet.getId());
        hold.setAmount(request.amount());
        hold.setFeeAmount(request.fee());
        hold.setStatus(EscrowStatus.HELD);

        escrowHoldRepository.save(hold);
    }

    @Transactional
    public void openDispute(UUID orderId) {
        EscrowHold hold = getHoldForUpdate(orderId);
        requireStatus(hold, EscrowStatus.HELD);

        hold.setStatus(EscrowStatus.DISPUTED);
        escrowHoldRepository.save(hold);
    }
    @Transactional
    public void closeDispute(UUID orderId){
        EscrowHold hold = getHoldForUpdate(orderId);
        requireStatus(hold, EscrowStatus.DISPUTED);
        hold.setStatus(EscrowStatus.HELD);
        escrowHoldRepository.save(hold);
    }
    //automated settlement
    @Transactional
    public boolean settle(UUID orderId) {
        EscrowHold hold = getHoldForUpdate(orderId);

        if(!hold.getStatus().equals(EscrowStatus.HELD)) return false;

        Wallet sellerWallet = walletRepository.findByIdForUpdate(hold.getSellerWalletId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));

        //money sellers actually receive
        BigDecimal amount = hold.getAmount();
        BigDecimal fee = hold.getFeeAmount();
        BigDecimal net = amount.subtract(fee);
        requireHeld(sellerWallet, amount);
        sellerWallet.setHeldBalance(sellerWallet.getHeldBalance().subtract(amount));
        sellerWallet.setAvailableBalance(net);
        walletRepository.save(sellerWallet);

        transactionRepository.save(WalletTransaction.create(
                sellerWallet, TransactionType.CAPTURE, net, "ORDER", hold.getOrderId().toString(),
                "capture-seller:" + orderId, "Earnings from order " + orderId));
        if (fee.signum() > 0) {
            transactionRepository.save(WalletTransaction.create(
                    sellerWallet, TransactionType.FEE, fee, "ORDER", hold.getOrderId().toString(),
                    "fee:" + orderId, "Platform fee"));
        }
        hold.setCapturedAmount(amount);
        hold.setStatus(EscrowStatus.CAPTURED);
        hold.setResolvedAt(Instant.now());
        escrowHoldRepository.save(hold);
        return true;
    }


    //refund for buyer
    @Transactional
    public void refundToBuyerWallet(UUID orderId){
        EscrowHold hold = getHoldForUpdate(orderId);

        if(!hold.getStatus().equals(EscrowStatus.HELD) && !hold.getStatus().equals(EscrowStatus.DISPUTED)){
            throw new AppException(ErrorCode.INVALID_ESCROW_STATE);
        }

        Wallet sellerWallet = walletRepository.findByIdForUpdate(hold.getSellerWalletId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        Wallet buyerWallet = walletRepository.findByIdForUpdate(hold.getBuyerWalletId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));

        BigDecimal amount = hold.getAmount();
        requireHeld(sellerWallet, amount);

        sellerWallet.setHeldBalance(sellerWallet.getHeldBalance().subtract(amount));
        buyerWallet.setAvailableBalance(buyerWallet.getAvailableBalance().add(amount));
        walletRepository.save(sellerWallet);
        walletRepository.save(buyerWallet);

        transactionRepository.save(WalletTransaction.create(
                sellerWallet, TransactionType.RELEASE, amount, "ORDER", hold.getOrderId().toString(),
                "refund-out:" + orderId, "Order refunded to the buyer"
        ));
        transactionRepository.save(WalletTransaction.create(
                buyerWallet, TransactionType.REFUND, amount, "ORDER", hold.getOrderId().toString(),
                "refund-in:" + orderId, "Refund for order " + orderId
        ));

        hold.setReleasedAmount(amount);
        hold.setStatus(EscrowStatus.RELEASED);
        hold.setResolvedAt(Instant.now());
        escrowHoldRepository.save(hold);
    }

    //admin manually transfers refund to direct the buyers' bank
    @Transactional
    public void payoutRefund(UUID orderId){
        EscrowHold hold = getHoldForUpdate(orderId);
        Wallet sellerWallet = walletRepository.findByIdForUpdate(hold.getSellerWalletId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        BigDecimal amount = hold.getAmount();
        requireHeld(sellerWallet, amount);
        sellerWallet.setHeldBalance(sellerWallet.getHeldBalance().subtract(amount));
        walletRepository.save(sellerWallet);
        transactionRepository.save(WalletTransaction.create(
            sellerWallet, TransactionType.REFUND_PAYOUT, amount, "ORDER", hold.getOrderId().toString(),
            "refund-payout:"+orderId, "Refund paid out to the buyer's bank"
        ));
        hold.setReleasedAmount(amount);
        hold.setStatus(EscrowStatus.RELEASED);
        hold.setResolvedAt(Instant.now());
        escrowHoldRepository.save(hold);
    }


    //helpers
    private EscrowHold getHoldForUpdate(UUID orderId) {
        return escrowHoldRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ESCROW_NOT_FOUND));
    }

    private void requireStatus(EscrowHold hold, EscrowStatus expected) {
        if (hold.getStatus() != expected) {
            throw new AppException(ErrorCode.INVALID_ESCROW_STATE);
        }
    }
    private void requireHeld(Wallet seller, BigDecimal amount) {
        if (seller.getHeldBalance().signum() < 0) {
            throw new AppException(ErrorCode.WALLET_INSUFFICIENT_HELD_BALANCE);
        }
    }
}
