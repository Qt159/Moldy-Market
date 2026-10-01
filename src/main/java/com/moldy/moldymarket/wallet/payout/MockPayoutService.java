package com.moldy.moldymarket.wallet.payout;

import com.moldy.moldymarket.wallet.entity.Withdrawal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MockPayoutService implements PayoutService {

    @Override
    public PayoutResult initiatePayout(Withdrawal withdrawal) {
        log.info("[MOCK PAYOUT] Transfer {} to {} - {} (withdrawal #{})",
                withdrawal.getAmount(), withdrawal.getBankName(),
                withdrawal.getBankAccountNumber(), withdrawal.getId());
        return PayoutResult.SUCCESS;
    }
}
