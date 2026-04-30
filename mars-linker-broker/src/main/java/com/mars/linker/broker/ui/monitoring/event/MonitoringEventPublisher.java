package com.mars.linker.broker.ui.monitoring.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 核心链路上调用，仅发布异步事件，不阻塞。
 */
@Component
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MonitoringEventPublisher.class);
    private final ApplicationEventPublisher publisher;

    public MonitoringEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishChange(String eventType, String metricName, double metricValue, String severity) {
        try {
            publisher.publishEvent(new MonitoringChangeEvent(this, eventType, metricName, metricValue, severity));
        } catch (Exception e) {
            log.debug("Failed to publish monitoring event: {}", e.getMessage());
        }
    }
}
