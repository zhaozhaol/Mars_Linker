package com.mars.linker.broker.ui.monitoring.isolation;

import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 监控资源预算约束。
 */
public class MonitoringResourceBudget {

    private final MarsLinkerUiProperties props;
    private final AtomicLong currentHistoryMemoryBytes = new AtomicLong();

    public MonitoringResourceBudget(MarsLinkerUiProperties props) {
        this.props = props;
    }

    public boolean checkHistoryMemory() {
        long maxBytes = (long) props.getMaxHistoryMemoryMB() * 1024 * 1024;
        return currentHistoryMemoryBytes.get() < maxBytes;
    }

    public boolean checkPushConnectionLimit(int currentConnections) {
        return currentConnections < props.getMaxPushConnections();
    }

    public void addHistoryMemory(long bytes) {
        currentHistoryMemoryBytes.addAndGet(bytes);
    }

    public void reduceHistoryMemory(long bytes) {
        currentHistoryMemoryBytes.updateAndGet(v -> Math.max(0, v - bytes));
    }

    public long getCurrentHistoryMemoryBytes() {
        return currentHistoryMemoryBytes.get();
    }

    public int getMaxPushConnections() {
        return props.getMaxPushConnections();
    }

    public int getMaxApiQueriesPerSecond() {
        return props.getMaxApiQueriesPerSecond();
    }
}
