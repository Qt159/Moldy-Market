package com.moldy.moldymarket.offer.jobs;

import com.moldy.moldymarket.offer.service.OfferService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OfferExpirationScheduler {
    OfferService offerService;
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void expireOffers(){
        offerService.expirePendingOffers();
    }
}
