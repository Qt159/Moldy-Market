package com.moldy.moldymarket.payment.webhook.sepay;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.payment.dto.request.PaymentTransactionWebhookRequest;
import com.moldy.moldymarket.payment.enums.TransferType;
import com.moldy.moldymarket.payment.webhook.IncomingTransfer;
import com.moldy.moldymarket.payment.webhook.WebhookProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SePayWebhookProvider implements WebhookProvider {

    public static final String PROVIDER = "SEPAY";
    private static final DateTimeFormatter SEPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final SePayWebhookVerifier verifier;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "sepay";
    }

    @Override
    public void verify(String rawBody, HttpHeaders headers) { verifier.verify(rawBody, headers);}

    @Override
    public IncomingTransfer parse(String rawBody) {
        PaymentTransactionWebhookRequest r;
        try{
            r = objectMapper.readValue(rawBody, PaymentTransactionWebhookRequest.class);
        }catch (Exception e){
            throw new AppException(ErrorCode.WEBHOOK_INVALID_REQUEST);
        }

        if (r.id() == null || r.transferType() == null || r.transferAmount() == null
                || r.content() == null || r.accountNumber() == null || r.transactionDate() == null
                || r.transferAmount().signum() <= 0) {
            throw new AppException(ErrorCode.WEBHOOK_INVALID_REQUEST);
        }
        TransferType type = "in".equalsIgnoreCase(r.transferType()) ? TransferType.IN : TransferType.OUT;

        return new IncomingTransfer(
                PROVIDER,
                String.valueOf(r.id()),
                parseTime(r.transactionDate()),
                r.accountNumber(),
                r.gateway(),
                blankToNull(r.subAccount()),
                r.content(),
                blankToNull(r.description()),
                blankToNull(r.code()),
                type,
                r.transferAmount(),
                blankToNull(r.referenceCode()),
                rawBody);
    }

    @Override
    public Object successBody() {
        return Map.of("success", true);
    }

    private Instant parseTime(String value){
        try{
            return LocalDateTime.parse(value, SEPAY_TIME).atZone(VIETNAM).toInstant();
        }catch (DateTimeParseException e){
            log.warn("Unexpected SePay transactionDate format: {}", value);
            return Instant.now();
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
