package com.mars.linker.broker.ui.monitoring.model;

/**
 * 监控事件（阈值触发/恢复）。
 */
public class MonitoringEvent {

    /** 事件唯一标识。 */
    private String eventId;
    /** 事件类型。 */
    private String eventType;
    /** 严重级别。 */
    private String severity;
    /** 关联指标名称。 */
    private String metricName;
    /** 指标实际值。 */
    private double metricValue;
    /** 阈值。 */
    private double threshold;
    /** 触发时间戳（毫秒）。 */
    private long triggerTime;
    /** 恢复时间戳（毫秒），null 表示未恢复。 */
    private Long resolvedTime;

    public MonitoringEvent() {}

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }
    public double getMetricValue() { return metricValue; }
    public void setMetricValue(double metricValue) { this.metricValue = metricValue; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public long getTriggerTime() { return triggerTime; }
    public void setTriggerTime(long triggerTime) { this.triggerTime = triggerTime; }
    public Long getResolvedTime() { return resolvedTime; }
    public void setResolvedTime(Long resolvedTime) { this.resolvedTime = resolvedTime; }
}
