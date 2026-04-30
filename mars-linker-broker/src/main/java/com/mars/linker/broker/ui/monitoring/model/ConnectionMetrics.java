package com.mars.linker.broker.ui.monitoring.model;

/**
 * 连接指标数据。
 */
public class ConnectionMetrics {

    /** 采集时间戳（毫秒）。 */
    private long timestamp;
    /** 当前活跃连接数。 */
    private long connectionsActive;
    /** 历史峰值总连接数。 */
    private long connectionsPeakTotal;
    /** 累计接受连接数。 */
    private long connectAcceptedTotal;
    /** 累计拒绝连接数。 */
    private long connectRejectedTotal;

    public ConnectionMetrics() {}

    public ConnectionMetrics(long timestamp, long connectionsActive, long connectionsPeakTotal,
                             long connectAcceptedTotal, long connectRejectedTotal) {
        this.timestamp = timestamp;
        this.connectionsActive = connectionsActive;
        this.connectionsPeakTotal = connectionsPeakTotal;
        this.connectAcceptedTotal = connectAcceptedTotal;
        this.connectRejectedTotal = connectRejectedTotal;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public long getConnectionsActive() { return connectionsActive; }
    public void setConnectionsActive(long connectionsActive) { this.connectionsActive = connectionsActive; }
    public long getConnectionsPeakTotal() { return connectionsPeakTotal; }
    public void setConnectionsPeakTotal(long connectionsPeakTotal) { this.connectionsPeakTotal = connectionsPeakTotal; }
    public long getConnectAcceptedTotal() { return connectAcceptedTotal; }
    public void setConnectAcceptedTotal(long connectAcceptedTotal) { this.connectAcceptedTotal = connectAcceptedTotal; }
    public long getConnectRejectedTotal() { return connectRejectedTotal; }
    public void setConnectRejectedTotal(long connectRejectedTotal) { this.connectRejectedTotal = connectRejectedTotal; }
}
