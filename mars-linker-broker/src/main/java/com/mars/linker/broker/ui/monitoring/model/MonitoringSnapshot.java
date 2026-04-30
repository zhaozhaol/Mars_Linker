package com.mars.linker.broker.ui.monitoring.model;

import java.util.Map;

/**
 * 监控快照，按类别携带指标数据。
 */
public class MonitoringSnapshot {

    /** 快照时间戳（毫秒）。 */
    private long timestamp;
    /** 指标类别。 */
    private String category;
    /** 指标数据键值对。 */
    private Map<String, Object> data;

    public MonitoringSnapshot() {}

    public MonitoringSnapshot(long timestamp, String category, Map<String, Object> data) {
        this.timestamp = timestamp;
        this.category = category;
        this.data = data;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
