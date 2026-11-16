package com.moldy.moldymarket.wallet.service;

import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.security.annotation.IsAdmin;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
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
    OrderRepository orderRepository;

    @IsAdmin
    @Transactional
    public void recordReceivedPayment(Order order) {
        BigDecimal amount = order.getTotalAmount();
        if (amount.signum() <= 0 || order.getBuyerId().equals(order.getSellerId())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid order amount or participants");
        }
        String orderId = order.getId().toString();
        if (escrowHoldRepository.findByOrderId(orderId).isPresent()) {
            throw new AppException(ErrorCode.INVALID_ESCROW_STATE);
        }
        Wallet buyer = walletRepository.findByUserIdForUpdate(order.getBuyerId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));
        Wallet seller = walletRepository.findByUserId(order.getSellerId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_EXISTED));

        // The bank transfer has already been received by the admin. Record it directly as held funds.
        buyer.setHeldBalance(buyer.getHeldBalance().add(amount));
        transactionRepository.save(WalletTransaction.create(
                buyer, TransactionType.HOLD, amount, "ORDER", orderId,
                "hold:" + orderId, "Admin confirmed receipt of the bank transfer"
        ));

        EscrowHold hold = new EscrowHold();
        hold.setOrderId(orderId);
        hold.setBuyerWalletId(buyer.getId());
        hold.setSellerWalletId(seller.getId());
        hold.setAmount(amount);
        hold.setFeeAmount(BigDecimal.ZERO);
        hold.setStatus(EscrowStatus.HELD);
        escrowHoldRepository.save(hold);
    }

    @Transactional
    @IsAdmin
    public EscrowHoldResponse capture(String orderId) {
        EscrowHold hold = getHoldForUpdate(orderId);
        requireStatus(hold, EscrowStatus.HELD);

        applyCapture(hold);
        hold.setStatus(EscrowStatus.CAPTURED);
        updateOrder(hold, OrderStatus.COMPLETED);
        hold.setResolvedAt(Instant.now());
        return escrowMapper.toEscrowHoldResponse(escrowHoldRepository.save(hold));
    }

    @Transactional
    @IsAdmin
    public EscrowHoldResponse release(String orderId) {
        EscrowHold hold = getHoldForUpdate(orderId);
        requireStatus(hold, EscrowStatus.HELD);

        applyRelease(hold);
        hold.setStatus(EscrowStatus.RELEASED);
        updateOrder(hold, OrderStatus.REFUNDED);
        hold.setResolvedAt(Instant.now());
        return escrowMapper.toEscrowHoldResponse(escrowHoldRepository.save(hold));
    }

    @Transactional
    @IsAdmin
    public EscrowHoldResponse openDispute(String orderId) {
        EscrowHold hold = getHoldForUpdate(orderId);
        requireStatus(hold, EscrowStatus.HELD);

        hold.setStatus(EscrowStatus.DISPUTED);
        updateOrder(hold, OrderStatus.DISPUTED);
        return escrowMapper.toEscrowHoldResponse(escrowHoldRepository.save(hold));
    }

    @Transactional
    @IsAdmin
    public EscrowHoldResponse resolveDispute(String orderId, DisputeOutcome outcome) {
        EscrowHold hold = getHoldForUpdate(orderId);
        requireStatus(hold, EscrowStatus.DISPUTED);

        if (outcome == DisputeOutcome.PAY_SELLER) {
            applyCapture(hold);
            hold.setStatus(EscrowStatus.CAPTURED);
            updateOrder(hold, OrderStatus.COMPLETED);
        } else {
            applyRelease(hold);
            hold.setStatus(EscrowStatus.RELEASED);
            updateOrder(hold, OrderStatus.REFUNDED);
        }

        hold.setResolvedAt(Instant.now());
        return escrowMapper.toEscrowHoldResponse(escrowHoldRepository.save(hold));
    }


    private void updateOrder(EscrowHold hold, OrderStatus status) {
        Order order = orderRepository.findById(UUID.fromString(hold.getOrderId()))
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));
        order.setStatus(status);
        if (status == OrderStatus.COMPLETED) order.setCompletedAt(Instant.now());
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
