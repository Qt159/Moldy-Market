package com.moldy.moldymarket.common.ratelimit;

public interface RateLimiter {
    /** Tiêu 1 lượt. Hết lượt thì ném RateLimitExceededException. */
    void check(RateLimitPolicy policy, String key);
}
