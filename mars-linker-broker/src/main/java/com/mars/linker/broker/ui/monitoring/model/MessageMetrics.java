package com.mars.linker.broker.ui.monitoring.model;

/**
 * 消息吞吐指标数据。
 */
public class MessageMetrics {

    /** 采集时间戳（毫秒）。 */
    private long timestamp;
    /** 累计入站消息数。 */
    private long publishInTotal;
    /** 累计出站消息数。 */
    private long publishOutTotal;
    /** 入站消息速率（条/秒）。 */
    private double publishInRate;
    /** 出站消息速率（条/秒）。 */
    private double publishOutRate;

    public MessageMetrics() {}

    public MessageMetrics(long timestamp, long publishInTotal, long publishOutTotal,
                          double publishInRate, double publishOutRate) {
        this.timestamp = timestamp;
        this.publishInTotal = publishInTotal;
        this.publishOutTotal = publishOutTotal;
        this.publishInRate = publishInRate;
        this.publishOutRate = publishOutRate;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public long getPublishInTotal() { return publishInTotal; }
    public void setPublishInTotal(long publishInTotal) { this.publishInTotal = publishInTotal; }
    public long getPublishOutTotal() { return publishOutTotal; }
    public void setPublishOutTotal(long publishOutTotal) { this.publishOutTotal = publishOutTotal; }
    public double getPublishInRate() { return publishInRate; }
    public void setPublishInRate(double publishInRate) { this.publishInRate = publishInRate; }
    public double getPublishOutRate() { return publishOutRate; }
    public void setPublishOutRate(double publishOutRate) { this.publishOutRate = publishOutRate; }
}
