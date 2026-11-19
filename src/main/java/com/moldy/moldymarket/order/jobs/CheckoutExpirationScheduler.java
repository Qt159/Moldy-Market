package com.moldy.moldymarket.order.jobs;

import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import com.moldy.moldymarket.order.repository.CheckoutGroupRepository;
import com.moldy.moldymarket.order.service.CheckoutExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutExpirationScheduler {
    private final CheckoutExpirationService checkoutExpirationService;
    private final CheckoutGroupRepository checkoutGroupRepository;

    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void expireCheckouts(){
        var ids = checkoutGroupRepository.findExpiredCheckoutIds(
                Instant.now(), CheckoutGroupStatus.PENDING_PAYMENT);
        for(UUID id : ids){
            try{
                checkoutExpirationService.expireOne(id);
            }catch (Exception e){
                log.error("Failed to expire checkout group: {}", id, e);
            }
        }
        checkoutExpirationService.expireExpiredCheckouts();
    }
}
