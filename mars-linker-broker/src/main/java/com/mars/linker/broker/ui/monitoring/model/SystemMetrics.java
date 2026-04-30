package com.mars.linker.broker.ui.monitoring.model;

/**
 * 系统资源指标数据。
 */
public class SystemMetrics {

    /** 采集时间戳（毫秒）。 */
    private long timestamp;
    /** 堆内存使用比率（0.0-1.0）。 */
    private double heapUsedRatio;
    /** CPU 使用比率（0.0-1.0）。 */
    private double cpuUsageRatio;
    /** 当前线程数。 */
    private int threadCount;
    /** GC 总次数。 */
    private long gcCount;
    /** GC 总耗时（毫秒）。 */
    private long gcTimeMs;

    public SystemMetrics() {}

    public SystemMetrics(long timestamp, double heapUsedRatio, double cpuUsageRatio,
                         int threadCount, long gcCount, long gcTimeMs) {
        this.timestamp = timestamp;
        this.heapUsedRatio = heapUsedRatio;
        this.cpuUsageRatio = cpuUsageRatio;
        this.threadCount = threadCount;
        this.gcCount = gcCount;
        this.gcTimeMs = gcTimeMs;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public double getHeapUsedRatio() { return heapUsedRatio; }
    public void setHeapUsedRatio(double heapUsedRatio) { this.heapUsedRatio = heapUsedRatio; }
    public double getCpuUsageRatio() { return cpuUsageRatio; }
    public void setCpuUsageRatio(double cpuUsageRatio) { this.cpuUsageRatio = cpuUsageRatio; }
    public int getThreadCount() { return threadCount; }
    public void setThreadCount(int threadCount) { this.threadCount = threadCount; }
    public long getGcCount() { return gcCount; }
    public void setGcCount(long gcCount) { this.gcCount = gcCount; }
    public long getGcTimeMs() { return gcTimeMs; }
    public void setGcTimeMs(long gcTimeMs) { this.gcTimeMs = gcTimeMs; }
}
