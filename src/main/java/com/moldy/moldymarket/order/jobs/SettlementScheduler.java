package com.moldy.moldymarket.order.jobs;

import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.order.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final OrderRepository orderRepository;
    private final SettlementService settlementService;

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void settlement(){
        List<UUID> ids = orderRepository.findDueForSettlementIds(Instant.now());
        for(UUID id : ids){
            try{
                settlementService.settleOne(id);
            }catch (Exception e){
                log.error("Settlement failed for order {}", id, e);
            }
        }
    }
}
