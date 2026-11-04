package com.moldy.moldymarket.payment.controller;

import com.moldy.moldymarket.payment.service.PaymentTransactionService;
import com.moldy.moldymarket.payment.webhook.IncomingTransfer;
import com.moldy.moldymarket.payment.webhook.WebhookProvider;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/payment_transactions/webhook")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentTransactionController {
    Map<String, WebhookProvider> providers;
    PaymentTransactionService paymentTransactionService;

    public PaymentTransactionController(List<WebhookProvider> providerList,
                                        PaymentTransactionService paymentTransactionService){
        this.providers = providerList.stream().collect(Collectors.toMap(WebhookProvider::name, p -> p));
        this.paymentTransactionService = paymentTransactionService;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Object> receive(@PathVariable String provider,
                                           @RequestBody String rawBody,
                                           @RequestHeader HttpHeaders headers) {
        WebhookProvider webhookProvider = providers.get(provider.toLowerCase());
        if (webhookProvider == null) {
            return ResponseEntity.notFound().build();
        }

        webhookProvider.verify(rawBody, headers);                       // 401 when not from the provider
        IncomingTransfer transfer = webhookProvider.parse(rawBody);     // 400 when malformed

        try {
            paymentTransactionService.process(transfer);
        } catch (DataIntegrityViolationException e) {
            if (!paymentTransactionService.alreadyReceived(transfer.provider(), transfer.externalId())) {
                throw e;
            }
            log.info("Duplicate webhook ignored: provider={}, externalId={}",
                    transfer.provider(), transfer.externalId());
        }

        return ResponseEntity.ok(webhookProvider.successBody());
    }
}
