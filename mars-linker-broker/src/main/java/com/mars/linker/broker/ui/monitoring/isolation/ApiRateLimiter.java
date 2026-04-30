package com.mars.linker.broker.ui.monitoring.isolation;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * API 查询限流器（令牌桶，无 Guava 依赖）。
 */
public class ApiRateLimiter {

    private final AtomicInteger availableTokens;
    private volatile int maxPerSecond;
    private volatile long lastRefillTime;

    public ApiRateLimiter(int maxPerSecond) {
        this.maxPerSecond = maxPerSecond;
        this.availableTokens = new AtomicInteger(maxPerSecond);
        this.lastRefillTime = System.currentTimeMillis();
    }

    public boolean tryAcquire() {
        refill();
        while (true) {
            int current = availableTokens.get();
            if (current <= 0) return false;
            if (availableTokens.compareAndSet(current, current - 1)) return true;
        }
    }

    public void updateRate(int newMaxPerSecond) {
        this.maxPerSecond = newMaxPerSecond;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        if (now - lastRefillTime >= 1000) {
            availableTokens.set(maxPerSecond);
            lastRefillTime = now;
        }
    }
}
