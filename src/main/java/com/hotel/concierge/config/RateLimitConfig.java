package com.hotel.concierge.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitConfig {

    @Value("${rate-limit.requests-per-minute}")
    private int requestsPerMinute;

    @Value("${rate-limit.burst-capacity}")
    private int burstCapacity;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, this::createBucket);
    }

    private Bucket createBucket(String key) {
        Bandwidth limit = Bandwidth.classic(requestsPerMinute,
                Refill.greedy(requestsPerMinute, Duration.ofMinutes(1)));
        Bandwidth burst = Bandwidth.classic(burstCapacity,
                Refill.intervally(burstCapacity, Duration.ofSeconds(1)));
        return Bucket.builder()
                .addLimit(limit)
                .addLimit(burst)
                .build();
    }
}
