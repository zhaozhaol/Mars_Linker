package com.mars.linker.broker.netty.protocol;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 单主题令牌桶限流器，线程安全。
 * <p>
 * 设计决策：
 * - refill() 使用 AtomicLong CAS 替代 synchronized，消除多线程锁竞争
 * - tryAcquire() 使用 AtomicInteger CAS 循环保证原子扣减
 * - LongAdder 记录通过/拒绝计数，高并发下无锁
 * </p>
 */
public class TokenBucket {

    private final AtomicInteger availableTokens;
    private volatile int maxPerSecond;
    private final AtomicLong lastRefillTime;
    private final LongAdder allowedCount = new LongAdder();
    private final LongAdder rejectedCount = new LongAdder();

    public TokenBucket(int maxPerSecond) {
        this.maxPerSecond = maxPerSecond;
        this.availableTokens = new AtomicInteger(maxPerSecond);
        this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
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

    private void refill() {
        long now = System.currentTimeMillis();
        long last = lastRefillTime.get();
        if (now - last < 1000) {
            return;
        }
        // CAS: 只有一个线程能成功更新 lastRefillTime 并重置令牌
        if (lastRefillTime.compareAndSet(last, now)) {
            availableTokens.set(maxPerSecond);
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
