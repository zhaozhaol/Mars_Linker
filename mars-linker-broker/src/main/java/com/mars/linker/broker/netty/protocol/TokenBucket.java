package com.mars.linker.broker.netty.protocol;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 单主题令牌桶限流器，线程安全。
 * <p>
 * 设计决策：
 * - refill() 使用 synchronized 保护，消除多线程同时 refill 窗口的竞态
 * - tryAcquire() 使用 AtomicInteger CAS 循环保证原子扣减
 * - LongAdder 记录通过/拒绝计数，高并发下无锁
 * </p>
 */
public class TokenBucket {

    private final AtomicInteger availableTokens;
    private volatile int maxPerSecond;
    private volatile long lastRefillTime;
    private final LongAdder allowedCount = new LongAdder();
    private final LongAdder rejectedCount = new LongAdder();

    public TokenBucket(int maxPerSecond) {
        this.maxPerSecond = maxPerSecond;
        this.availableTokens = new AtomicInteger(maxPerSecond);
        this.lastRefillTime = System.currentTimeMillis();
    }

    /**
     * 尝试获取1个令牌。
     *
     * @return true 表示允许通过，false 表示超限拒绝
     */
    public boolean tryAcquire() {
        refill();
        while (true) {
            int current = availableTokens.get();
            if (current <= 0) {
                rejectedCount.increment();
                return false;
            }
            if (availableTokens.compareAndSet(current, current - 1)) {
                allowedCount.increment();
                return true;
            }
        }
    }

    private synchronized void refill() {
        long now = System.currentTimeMillis();
        if (now - lastRefillTime >= 1000) {
            availableTokens.set(maxPerSecond);
            lastRefillTime = now;
        }
    }

    public void updateRate(int newMaxPerSecond) {
        this.maxPerSecond = newMaxPerSecond;
    }

    public int getMaxPerSecond() { return maxPerSecond; }
    public int getAvailableTokens() { return availableTokens.get(); }
    public long getAllowedCount() { return allowedCount.sum(); }
    public long getRejectedCount() { return rejectedCount.sum(); }

    public void resetCounters() {
        allowedCount.reset();
        rejectedCount.reset();
    }
}
