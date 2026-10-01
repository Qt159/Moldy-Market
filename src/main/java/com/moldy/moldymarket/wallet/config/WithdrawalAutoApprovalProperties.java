package com.moldy.moldymarket.wallet.config;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = "wallet.withdrawal.auto-approval")
public class WithdrawalAutoApprovalProperties {
    boolean enabled = false;
    BigDecimal maxAmount = BigDecimal.ZERO;
    BigDecimal maxDailyTotal = BigDecimal.ZERO;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }

    public BigDecimal getMaxDailyTotal() { return maxDailyTotal; }
    public void setMaxDailyTotal(BigDecimal maxDailyTotal) { this.maxDailyTotal = maxDailyTotal; }
}
