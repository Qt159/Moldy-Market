package com.moldy.moldymarket.order.jobs;

import com.moldy.moldymarket.product.service.CheckoutExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class CheckoutExpirationScheduler {
    private final CheckoutExpirationService checkoutExpirationService;

    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void expireCheckouts(){
        checkoutExpirationService.expireExpiredCheckouts();
    }
}
