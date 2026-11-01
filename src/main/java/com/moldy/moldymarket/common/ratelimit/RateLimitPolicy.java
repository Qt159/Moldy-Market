package com.moldy.moldymarket.common.ratelimit;

import java.time.Duration;

public enum RateLimitPolicy {
    LOGIN_PER_IP(30, Duration.ofMinutes(5)),
    LOGIN_PER_ACCOUNT(10, Duration.ofMinutes(15)),
    REGISTER_PER_IP(5, Duration.ofMinutes(1)),
    PRODUCT_CREATE(10, Duration.ofMinutes(1)),
    SUGGEST_PER_IP(60, Duration.ofMinutes(1)),
    SEARCH_PER_IP(120, Duration.ofMinutes(1)),

    ;

    private final int capacity;
    private final Duration period;

    RateLimitPolicy(int capacity, Duration period) {
        this.capacity = capacity;
        this.period = period;
    }

    public int capacity() { return capacity; }
    public Duration period() { return period; }

}
