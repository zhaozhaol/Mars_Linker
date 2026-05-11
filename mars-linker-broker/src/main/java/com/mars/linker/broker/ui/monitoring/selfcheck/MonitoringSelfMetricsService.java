package com.mars.linker.broker.ui.monitoring.selfcheck;

import com.mars.linker.broker.ui.monitoring.event.MonitoringEventPublisher;
import com.mars.linker.broker.ui.monitoring.isolation.MonitoringFaultBoundary;
import com.mars.linker.broker.ui.monitoring.model.MonitoringSelfMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringSelfMetricsService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringSelfMetricsService.class);

    private final AtomicLong collectionTimeMs = new AtomicLong();
    private final AtomicLong pushDelayMs = new AtomicLong();
    private final AtomicLong historyMemoryBytes = new AtomicLong();
    private final AtomicInteger pushConnectionCount = new AtomicInteger();
    private final AtomicInteger threadPoolActiveCount = new AtomicInteger();
    private final AtomicInteger threadPoolQueueSize = new AtomicInteger();
    private final AtomicLong eventDiscardCount = new AtomicLong();

    private final MonitoringEventPublisher eventPublisher;

    public MonitoringSelfMetricsService(MonitoringEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void recordCollectionTime(long ms) {
        collectionTimeMs.set(ms);
        checkPerformanceDegradation();
    }

    public void recordPushDelay(long ms) {
        pushDelayMs.set(ms);
        checkPerformanceDegradation();
    }

    public void setHistoryMemoryBytes(long bytes) {
        historyMemoryBytes.set(bytes);
    }

    public void setPushConnectionCount(int count) {
        pushConnectionCount.set(count);
    }

    public void setThreadPoolStats(int active, int queueSize) {
        threadPoolActiveCount.set(active);
        threadPoolQueueSize.set(queueSize);
    }

    public void incrementEventDiscardCount() {
        eventDiscardCount.incrementAndGet();
    }

    public MonitoringSelfMetrics snapshot() {
        return new MonitoringSelfMetrics(
                System.currentTimeMillis(),
                collectionTimeMs.get(),
                pushDelayMs.get(),
                historyMemoryBytes.get(),
                pushConnectionCount.get(),
                threadPoolActiveCount.get(),
                threadPoolQueueSize.get(),
                eventDiscardCount.get() + MonitoringFaultBoundary.getDiscardCount()
        );
    }

    private void checkPerformanceDegradation() {
        if (collectionTimeMs.get() > 500 || pushDelayMs.get() > 2000) {
            MonitoringFaultBoundary.execute(() -> {
                eventPublisher.publishChange(
                        "MONITORING_PERFORMANCE_DEGRADED",
                        "monitoring.collectionTimeMs",
                        collectionTimeMs.get(),
                        "warning"
                );
            });
        }
    }
}
