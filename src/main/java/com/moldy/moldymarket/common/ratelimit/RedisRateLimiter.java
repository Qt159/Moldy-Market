package com.moldy.moldymarket.common.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "redis", matchIfMissing = true)
public class RedisRateLimiter implements RateLimiter {

    private static final Map<RateLimitPolicy, BucketConfiguration> CONFIGS = Arrays.stream(RateLimitPolicy.values())
            .collect(Collectors.toMap(
                    p -> p,
                    p -> BucketConfiguration.builder()
                            .addLimit(limit -> limit
                                    .capacity(p.capacity())
                                    .refillIntervally(p.capacity(), p.period()))
                            .build(),
                    (a, b) -> a,
                    () -> new EnumMap<>(RateLimitPolicy.class)));

    private final LettuceBasedProxyManager<byte[]> proxyManager;

    @Override
    public void check(RateLimitPolicy policy, String key) {
        ConsumptionProbe probe;
        try {
            BucketConfiguration config = CONFIGS.get(policy);
            Bucket bucket = proxyManager.getProxy(redisKey(policy, key), () -> config);
            probe = bucket.tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException e) {
            // Redis lỗi hoặc timeout: cho request đi qua (fail-open) để hệ thống không sập theo Redis
            log.error("Rate limiter unavailable, request allowed. policy={}, cause={}", policy, e.toString());
            return;
        }

        // Nằm NGOÀI try/catch để không bị nuốt
        if (!probe.isConsumed()) {
            long seconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
            throw new RateLimitExceededException(seconds);
        }
    }

    // Băm key: cố định độ dài (chống ai đó gửi email dài cả MB làm đầy Redis) và không lưu email dạng rõ vào Redis
    private static byte[] redisKey(RateLimitPolicy policy, String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return ("rl:" + policy.name() + ":" + HexFormat.of().formatHex(hash)).getBytes(StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
