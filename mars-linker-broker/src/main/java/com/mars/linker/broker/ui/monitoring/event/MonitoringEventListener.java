package com.mars.linker.broker.ui.monitoring.event;

import com.mars.linker.broker.ui.monitoring.isolation.MonitoringFaultBoundary;
import com.mars.linker.broker.ui.monitoring.sampling.SampleRateFilter;
import com.mars.linker.broker.ui.monitoring.sampling.TimeWindowAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringEventListener {

    private static final Logger log = LoggerFactory.getLogger(MonitoringEventListener.class);

    private final SampleRateFilter sampleRateFilter;
    private final TimeWindowAggregator timeWindowAggregator;

    public MonitoringEventListener(SampleRateFilter sampleRateFilter,
                                   TimeWindowAggregator timeWindowAggregator) {
        this.sampleRateFilter = sampleRateFilter;
        this.timeWindowAggregator = timeWindowAggregator;
    }

    @EventListener
    @Async("monitoring-executor")
    public void onMonitoringChangeEvent(MonitoringChangeEvent event) {
        MonitoringFaultBoundary.execute(() -> {
            String category = inferCategory(event.getMetricName());
            if (sampleRateFilter.shouldSample(category)) {
                timeWindowAggregator.onEvent(event);
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
}
