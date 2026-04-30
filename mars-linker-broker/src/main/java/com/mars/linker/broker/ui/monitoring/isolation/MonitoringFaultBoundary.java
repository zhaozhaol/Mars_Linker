package com.mars.linker.broker.ui.monitoring.isolation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 监控模块容错边界：捕获所有 Throwable，记录 WARN 日志，不重新抛出。
 */
public class MonitoringFaultBoundary {

    private static final Logger log = LoggerFactory.getLogger(MonitoringFaultBoundary.class);
    private static final AtomicLong exceptionCount = new AtomicLong();
    private static final AtomicLong discardCount = new AtomicLong();

    public static void execute(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            exceptionCount.incrementAndGet();
            log.warn("Monitoring fault boundary caught: {}", t.getMessage(), t);
        }
    }

    public static <T> T executeWithResult(Supplier<T> action, T defaultValue) {
        try {
            return action.get();
        } catch (Throwable t) {
            exceptionCount.incrementAndGet();
            log.warn("Monitoring fault boundary caught: {}", t.getMessage(), t);
            return defaultValue;
        }
    }

    public static void incrementDiscardCount() {
        discardCount.incrementAndGet();
    }

    public static long getExceptionCount() {
        return exceptionCount.get();
    }

    public static long getDiscardCount() {
        return discardCount.get();
    }
}
