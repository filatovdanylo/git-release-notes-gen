package me.automatedgitdiffnotesgenerator.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitConfig {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String ipAddress, String endpoint) {
        String cacheKey = ipAddress + ":" + endpoint;
        return buckets.computeIfAbsent(cacheKey, key -> createBucketForEndpoint(endpoint));
    }

    private Bucket createBucketForEndpoint(String endpoint) {
        if (endpoint.contains("/generate")) {
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(3).refillIntervally(3, Duration.ofMinutes(1)).build())
                    .build();
        } else {
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(10).refillIntervally(10, Duration.ofMinutes(1)).build())
                    .build();
        }
    }
}
