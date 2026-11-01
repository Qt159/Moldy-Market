package com.moldy.moldymarket.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "memory")
public class InMemoryRateLimiter implements RateLimiter {

    // Mỗi policy một cache riêng, bucket không dùng trong 2 giờ thì bị xóa, tối đa 100k key
    private final Map<RateLimitPolicy, Cache<String, Bucket>> caches = new ConcurrentHashMap<>();

    @Override
    public void check(RateLimitPolicy policy, String key) {
        Bucket bucket = caches
                .computeIfAbsent(policy, p -> Caffeine.newBuilder()
                        .expireAfterAccess(Duration.ofHours(2))
                        .maximumSize(100_000)
                        .build())
                .get(key, k -> Bucket.builder()
                        .addLimit(limit -> limit
                                .capacity(policy.capacity())
                                .refillIntervally(policy.capacity(), policy.period()))
                        .build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
            throw new RateLimitExceededException(seconds);
        }

    }

}
