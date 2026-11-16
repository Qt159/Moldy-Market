package com.moldy.moldymarket.wallet.payout;


import com.moldy.moldymarket.wallet.entity.Withdrawal;

public interface PayoutService {
    PayoutResult initiatePayout(Withdrawal withdrawal);
}
