package com.mars.linker.broker.ui.monitoring.sampling;

import com.mars.linker.broker.ui.monitoring.event.MonitoringChangeEvent;
import com.mars.linker.broker.ui.monitoring.isolation.MonitoringFaultBoundary;
import com.mars.linker.broker.ui.monitoring.model.MonitoringSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * 时间窗口聚合器：按 category 分组，窗口到期时 flush 为 MonitoringSnapshot。
 */
public class TimeWindowAggregator {

    private static final Logger log = LoggerFactory.getLogger(TimeWindowAggregator.class);

    private final ConcurrentHashMap<String, AggregationWindow> windows = new ConcurrentHashMap<>();
    private final long aggregationWindowMs;

    public TimeWindowAggregator(long aggregationWindowMs) {
        this.aggregationWindowMs = Math.max(500, Math.min(60000, aggregationWindowMs));
    }

    public void onEvent(MonitoringChangeEvent event) {
        MonitoringFaultBoundary.execute(() -> {
            String category = inferCategory(event.getMetricName());
            AggregationWindow window = windows.computeIfAbsent(category, k -> new AggregationWindow(k, aggregationWindowMs));
            window.update(event.getMetricValue());
            if (window.shouldFlush()) {
                MonitoringSnapshot snapshot = window.flush();
                log.debug("Flushed aggregation snapshot for category={}, count={}", category, snapshot.getData().get("count"));
            }
        });
    }

    private String inferCategory(String metricName) {
        if (metricName.startsWith("connections") || metricName.startsWith("connect.")) return "connection";
        if (metricName.startsWith("publish") || metricName.startsWith("message")) return "message";
        if (metricName.startsWith("subscription") || metricName.startsWith("topic")) return "subscription";
        if (metricName.startsWith("heap") || metricName.startsWith("cpu") || metricName.startsWith("gc")) return "system";
        return "other";
    }

    public Map<String, AggregationWindow> getWindows() {
        return windows;
    }

    /**
     * 聚合窗口数据结构。
     */
    public static class AggregationWindow {
        private final String category;
        private final long windowDurationMs;
        private volatile long windowStart;
        private final LongAdder count = new LongAdder();
        private final DoubleAdder sum = new DoubleAdder();
        private final AtomicReference<Double> max = new AtomicReference<>(Double.NEGATIVE_INFINITY);

        public AggregationWindow(String category, long windowDurationMs) {
            this.category = category;
            this.windowDurationMs = windowDurationMs;
            this.windowStart = System.currentTimeMillis();
        }

        public void update(double value) {
            count.increment();
            sum.add(value);
            max.accumulateAndGet(value, Math::max);
        }

        public boolean shouldFlush() {
            return System.currentTimeMillis() - windowStart >= windowDurationMs;
        }

        public MonitoringSnapshot flush() {
            MonitoringSnapshot snapshot = toSnapshot();
            reset();
            return snapshot;
        }

        public MonitoringSnapshot toSnapshot() {
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("count", count.sum());
            data.put("sum", sum.sum());
            data.put("max", max.get());
            data.put("avg", count.sum() > 0 ? sum.sum() / count.sum() : 0);
            return new MonitoringSnapshot(windowStart, category, data);
        }

        private void reset() {
            windowStart = System.currentTimeMillis();
            count.reset();
            sum.reset();
            max.set(Double.NEGATIVE_INFINITY);
        }
    }
}
