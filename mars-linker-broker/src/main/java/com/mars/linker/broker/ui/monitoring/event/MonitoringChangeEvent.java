package com.mars.linker.broker.ui.monitoring.event;

import org.springframework.context.ApplicationEvent;

/**
 * 监控指标变更异步事件（核心链路发布，采集侧异步监听）。
 */
public class MonitoringChangeEvent extends ApplicationEvent {

    private final String eventType;
    private final String metricName;
    private final double metricValue;
    private final String severity;

    public MonitoringChangeEvent(Object source, String eventType, String metricName,
                                double metricValue, String severity) {
        super(source);
        this.eventType = eventType;
        this.metricName = metricName;
        this.metricValue = metricValue;
        this.severity = severity;
    }

    public String getEventType() { return eventType; }
    public String getMetricName() { return metricName; }
    public double getMetricValue() { return metricValue; }
    public String getSeverity() { return severity; }
}
