package com.moldy.moldymarket.payment.service;

import com.moldy.moldymarket.payment.enums.MatchStatus;
import com.moldy.moldymarket.payment.enums.TransferType;
import com.moldy.moldymarket.payment.mapper.PaymentTransactionMapper;
import com.moldy.moldymarket.payment.repository.PaymentTransactionRepository;
import com.moldy.moldymarket.payment.webhook.IncomingTransfer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {
    private static final Pattern CODE_PATTERN = Pattern.compile("MM[A-Z0-9]{8}", Pattern.CASE_INSENSITIVE);
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentTransactionMapper paymentTransactionMapper;

    @Transactional(timeout = 5)
    public void process(IncomingTransfer incomingTransfer) {
        var transaction = paymentTransactionMapper.toPaymentTransaction(incomingTransfer);
        // A bank notification is evidence. Only an admin can confirm a payment.
        var status = incomingTransfer.type() == TransferType.OUT ? MatchStatus.IGNORED : MatchStatus.UNMATCHED;
        transaction.recordMatch(resolveCode(incomingTransfer), status);
        paymentTransactionRepository.saveAndFlush(transaction);
    }

    @Transactional(readOnly = true)
    public boolean alreadyReceived(String provider, String externalId){
        return paymentTransactionRepository.existsByProviderAndExternalId(provider, externalId);
    }

    private String resolveCode(IncomingTransfer incomingTransfer){
        if (incomingTransfer.paymentCode() != null && !incomingTransfer.paymentCode().isBlank()) {
            return incomingTransfer.paymentCode().trim().toUpperCase(Locale.ROOT);
        }
        var m = CODE_PATTERN.matcher(incomingTransfer.content() == null ? "" : incomingTransfer.content());
        return m.find() ? m.group().toUpperCase(Locale.ROOT) : null;
    }

}
