package com.moldy.moldymarket.order.jobs;

import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.service.CheckoutExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "checkout.expiration.enabled", havingValue = "true", matchIfMissing = true)
public class CheckoutExpirationScheduler {
    private final CheckoutGroupRepository checkoutGroupRepository;
    private final CheckoutExpirationService checkoutExpirationService;

    @Scheduled(fixedDelayString = "${checkout.expiration.interval-ms:60000}")
    public void expireCheckouts() {
        Instant cutoff = Instant.now();
        for (UUID id : checkoutGroupRepository.findExpiredCheckoutIds(cutoff)) {
            try {
                checkoutExpirationService.expire(id, cutoff);
            } catch (RuntimeException error) {
                log.error("Failed to expire checkout {}", id, error);
            }
        }
    }
}
