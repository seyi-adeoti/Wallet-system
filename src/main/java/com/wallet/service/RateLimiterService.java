



package com.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_TRANSFERS_PER_MINUTE = 5;
    private static final int MAX_LOGINS_PER_MINUTE    = 5;

    // Called before every transfer
    public boolean isTransferAllowed(UUID userId) {
        return checkLimit("rate:transfer:" + userId, MAX_TRANSFERS_PER_MINUTE);
    }

    // Called on every login attempt
    public boolean isLoginAllowed(String clientIp) {
        return checkLimit("rate:login:" + clientIp, MAX_LOGINS_PER_MINUTE);
    }

    private boolean checkLimit(String key, int maxRequests) {
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == 1) {
            // First hit — set 1-minute window
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        log.debug("Rate limit check → key: {} count: {}/{}", key, count, maxRequests);
        return count <= maxRequests;
    }

    // How many attempts remain (for response headers)
    public long getRemainingAttempts(String key, int maxRequests) {
        Object count = redisTemplate.opsForValue().get(key);
        if (count == null) return maxRequests;
        return Math.max(0, maxRequests - Long.parseLong(count.toString()));
    }
}