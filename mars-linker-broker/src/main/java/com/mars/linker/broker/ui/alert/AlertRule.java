package com.mars.linker.broker.ui.alert;

public class AlertRule {

    private String id;
    private String name;
    private String metric;
    private String operator;
    private double threshold;
    private int durationSeconds;
    private boolean enabled;
    private long createdAt;
    private long updatedAt;
    /** 严重级别：critical / warning / info。 */
    private String severity = "warning";
    /** 持续时间（毫秒）。 */
    private long durationMs;

    public AlertRule() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.enabled = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
