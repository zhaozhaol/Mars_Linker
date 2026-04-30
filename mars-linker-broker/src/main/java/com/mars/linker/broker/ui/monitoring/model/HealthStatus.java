package com.mars.linker.broker.ui.monitoring.model;

import java.util.List;

/**
 * 整体健康状态。
 */
public class HealthStatus {

    /** 采集时间戳（毫秒）。 */
    private long timestamp;
    /** 整体状态：UP / DOWN / DEGRADED。 */
    private String status;
    /** 各组件健康状态列表。 */
    private List<ComponentHealth> components;

    public HealthStatus() {}

    public HealthStatus(long timestamp, String status, List<ComponentHealth> components) {
        this.timestamp = timestamp;
        this.status = status;
        this.components = components;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<ComponentHealth> getComponents() { return components; }
    public void setComponents(List<ComponentHealth> components) { this.components = components; }
}
