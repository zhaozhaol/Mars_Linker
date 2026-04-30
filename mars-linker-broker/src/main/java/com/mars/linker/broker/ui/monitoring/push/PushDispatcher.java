package com.mars.linker.broker.ui.monitoring.push;

import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import com.mars.linker.broker.ui.monitoring.MetricCategoryService;
import com.mars.linker.broker.ui.monitoring.MonitoringService;
import com.mars.linker.broker.ui.monitoring.model.PushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);
    private final MetricCategoryService metricCategoryService;
    private final MonitoringSseHandler sseHandler;
    private final MonitoringService monitoringService;
    private final MarsLinkerUiProperties uiProperties;

    public PushDispatcher(MetricCategoryService metricCategoryService,
                          MonitoringSseHandler sseHandler,
                          MonitoringService monitoringService,
                          MarsLinkerUiProperties uiProperties) {
        this.metricCategoryService = metricCategoryService;
        this.sseHandler = sseHandler;
        this.monitoringService = monitoringService;
        this.uiProperties = uiProperties;
    }

    @Scheduled(fixedDelayString = "${mars.linker.ui.monitor-refresh-ms:5000}")
    public void dispatchMetrics() {
        monitoringService.calculateMessageRates(uiProperties.getMonitorRefreshMs());
        try {
            if (sseHandler.getConnectionCount() == 0) return;
            long now = System.currentTimeMillis();
            if (uiProperties.isConnectionEnabled()) {
                sseHandler.push(new PushMessage("metrics_update", "connection", now, metricCategoryService.connectionMetrics()));
            }
            if (uiProperties.isMessageEnabled()) {
                sseHandler.push(new PushMessage("metrics_update", "message", now, metricCategoryService.messageMetrics()));
            }
            if (uiProperties.isSubscriptionEnabled()) {
                sseHandler.push(new PushMessage("metrics_update", "subscription", now, metricCategoryService.subscriptionMetrics()));
            }
            if (uiProperties.isSystemEnabled()) {
                sseHandler.push(new PushMessage("metrics_update", "system", now, metricCategoryService.systemMetrics()));
            }
        } catch (Exception e) {
            log.debug("Push dispatch error: {}", e.getMessage());
        }
    }

    public void pushAlertEvent(Object alertEvent) {
        sseHandler.push(new PushMessage("alert_event", "all", System.currentTimeMillis(), alertEvent));
    }
}
