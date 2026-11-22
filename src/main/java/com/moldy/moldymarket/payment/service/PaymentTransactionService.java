package com.moldy.moldymarket.payment.service;

import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.entity.PaymentTransaction;
import com.moldy.moldymarket.payment.enums.MatchStatus;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.enums.TransferType;
import com.moldy.moldymarket.payment.event.PaymentCompletedEvent;
import com.moldy.moldymarket.payment.mapper.PaymentTransactionMapper;
import com.moldy.moldymarket.payment.repository.PaymentRepository;
import com.moldy.moldymarket.payment.repository.PaymentTransactionRepository;
import com.moldy.moldymarket.payment.webhook.IncomingTransfer;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.service.CheckoutExpirationService;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentTransactionService {
    static Pattern CODE_PATTERN = Pattern.compile("MM[A-Z0-9]{8}", Pattern.CASE_INSENSITIVE);
    PaymentRepository paymentRepository;
    PaymentTransactionRepository paymentTransactionRepository;
    ApplicationEventPublisher events;
    PaymentTransactionMapper paymentTransactionMapper;
    CheckoutGroupRepository checkoutGroupRepository;
    CheckoutExpirationService checkoutExpirationService;

    @Transactional(timeout = 5)
    public void process(IncomingTransfer incomingTransfer){
        //idempotency
        PaymentTransaction paymentTransaction = paymentTransactionRepository.saveAndFlush(paymentTransactionMapper.toPaymentTransaction(incomingTransfer));

        //record only: our own payouts and refunds
        if(incomingTransfer.type() == TransferType.OUT){
            paymentTransaction.recordMatch(null, MatchStatus.IGNORED);
            return;
        }

        //find the payment by its code
        String code = resolveCode(incomingTransfer);
        var checkoutId = code == null ? null : paymentRepository.findCheckoutGroupIdByPaymentCode(code).orElse(null);
        if(checkoutId == null){
            paymentTransaction.recordMatch(code, MatchStatus.UNMATCHED);
            return;
        }

        // Match the expiry/cancellation lock order; do not preload a stale Payment entity.
        var checkout = checkoutGroupRepository.findByIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_GROUP_NOT_EXISTED));
        Payment payment = paymentRepository.findByCheckoutGroupIdForUpdate(checkoutId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_EXISTED));
        if (payment.getMethod() != PaymentMethod.ONLINE_BANKING) {
            paymentTransaction.recordMatch(code, MatchStatus.UNMATCHED);
            return;
        }

        //check the received amount if it's equal to amount of saved payment
        if(payment.getAmount().compareTo(incomingTransfer.amount()) != 0){
            paymentTransaction.recordMatch(code, MatchStatus.AMOUNT_MISMATCH);
            log.warn("Amount mistmatch: paymentId = {}, expected={}, received={}, externalId={}",
                    payment.getId(), payment.getAmount(), incomingTransfer.amount(), incomingTransfer.externalId());
            return;
        }

        // Pending -> Successful
        Instant now = Instant.now();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            paymentTransaction.recordMatch(code, payment.getStatus() == PaymentStatus.SUCCESSFUL
                    ? MatchStatus.DUPLICATE : MatchStatus.LATE);
            return;
        }
        if (checkout.getStatus() != CheckoutGroupStatus.PENDING_PAYMENT
                || checkout.getExpiredAt() == null || !now.isBefore(checkout.getExpiredAt())) {
            checkoutExpirationService.expire(checkoutId, now);
            paymentTransaction.recordMatch(code, MatchStatus.LATE);
            return;
        }

        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setPaidAt(now);
        payment.setUpdatedAt(now);
        payment.setPaymentTransaction(paymentTransaction);
        paymentTransaction.recordMatch(code, MatchStatus.MATCHED);
        events.publishEvent(new PaymentCompletedEvent(
                payment.getId(), payment.getCheckoutGroupId(), payment.getPayerId(), payment.getAmount(), now));
        //todo: late/duplicate -> refund or re-reserve for buyers
    }

    @Transactional(readOnly = true)
    public boolean alreadyReceived(String provider, String externalId){
        return paymentTransactionRepository.existsByProviderAndExternalId(provider, externalId);
    }

    private String resolveCode(IncomingTransfer incomingTransfer){
        if (incomingTransfer.paymentCode() != null && !incomingTransfer.paymentCode().isBlank()) {
            return incomingTransfer.paymentCode().trim().toUpperCase();
        }
        if (incomingTransfer.content() == null) return null;
        Matcher m = CODE_PATTERN.matcher(incomingTransfer.content());
        return m.find() ? m.group().toUpperCase() : null;
    }

}
