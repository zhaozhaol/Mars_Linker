package com.mars.linker.broker.ui.alert;

public class AlertEvent {

    private String id;
    private String ruleId;
    private String ruleName;
    private String metric;
    private double actualValue;
    private double threshold;
    private String severity;
    private long triggeredAt;
    private long resolvedAt;
    private boolean active;
    /** 事件状态：firing / acknowledged / resolved / silenced。 */
    private String status = "firing";
    /** 静默截止时间戳（毫秒），null 表示未静默。 */
    private Long silencedUntil;
    /** 确认时间戳。 */
    private Long acknowledgedAt;
    /** 确认操作人。 */
    private String acknowledgedBy;
    /** 手动解决操作人。 */
    private String resolvedBy;
    /** 采样率（0.0~1.0），用于告警精度提示。 */
    private Double sampleRate;

    public AlertEvent() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public double getActualValue() { return actualValue; }
    public void setActualValue(double actualValue) { this.actualValue = actualValue; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public long getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(long triggeredAt) { this.triggeredAt = triggeredAt; }
    public long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(long resolvedAt) { this.resolvedAt = resolvedAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getSilencedUntil() { return silencedUntil; }
    public void setSilencedUntil(Long silencedUntil) { this.silencedUntil = silencedUntil; }
    public Long getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Long acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public Double getSampleRate() { return sampleRate; }
    public void setSampleRate(Double sampleRate) { this.sampleRate = sampleRate; }

    public void acknowledge(String user) {
        this.acknowledgedAt = System.currentTimeMillis();
        this.acknowledgedBy = user;
        this.status = "acknowledged";
    }

    public void manualResolve(String user) {
        this.active = false;
        this.resolvedAt = System.currentTimeMillis();
        this.resolvedBy = user;
        this.status = "resolved";
    }
}
