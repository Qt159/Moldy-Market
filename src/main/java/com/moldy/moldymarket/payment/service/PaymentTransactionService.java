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
        Payment payment = code == null ? null : paymentRepository.findByPaymentCode(code).orElse(null);
        if(payment == null){
            paymentTransaction.recordMatch(code, MatchStatus.UNMATCHED);
            log.warn("Unmatched transfer: provider={}, externalId={}, content={}",
                    incomingTransfer.provider(), incomingTransfer.externalId(), incomingTransfer.content());
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
        if(paymentRepository.markPaid(payment.getId(), paymentTransaction, now) == 1){
            paymentTransaction.recordMatch(code, MatchStatus.MATCHED);
            events.publishEvent(new PaymentCompletedEvent(
                    payment.getId(), payment.getCheckoutGroupId(),payment.getPayerId(), payment.getAmount(), now));

            log.info("Payment paid: paymentId={}, checkoutGroupId={}, externalId={}",
                    payment.getId(), payment.getCheckoutGroupId(), incomingTransfer.externalId());
        }
        else{
            PaymentStatus current = paymentRepository.findStatusById(payment.getId());
            MatchStatus status = current == PaymentStatus.SUCCESSFUL ? MatchStatus.DUPLICATE : MatchStatus.LATE;
            paymentTransaction.recordMatch(code, status);
            log.warn("Transfer for a payment that is {}: paymentId={}, externalId={}, result={}",
                    current, payment.getId(), incomingTransfer.externalId(), status);
        }
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
        Matcher m = CODE_PATTERN.matcher(incomingTransfer.content());
        return m.find() ? m.group().toUpperCase() : null;
    }

}
