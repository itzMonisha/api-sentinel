package com.sentinel.shared.security;

import com.sentinel.shared.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class RateLimiterService {

    private final Map<String, ConcurrentLinkedDeque<Instant>> attempts = new ConcurrentHashMap<>();
    private final int maxAttemptsPerMinute = 15;
    private final long windowSeconds = 60;

    public void checkRateLimit(String clientIp, String action) {
        String key = clientIp + ":" + action;
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);

        ConcurrentLinkedDeque<Instant> timestamps = attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        // Purge expired attempts outside the 60s sliding window
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxAttemptsPerMinute) {
            throw new ApiException(
                    "Too many requests from this IP address. Please wait a minute before trying again.",
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED"
            );
        }

        timestamps.addLast(now);
    }

    public void clear(String clientIp, String action) {
        attempts.remove(clientIp + ":" + action);
    }
}
