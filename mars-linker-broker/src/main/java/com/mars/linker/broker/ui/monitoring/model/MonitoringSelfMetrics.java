package com.mars.linker.broker.ui.monitoring.model;

/**
 * 监控模块自身运行指标。
 */
public class MonitoringSelfMetrics {

    /** 采集时间戳（毫秒）。 */
    private long timestamp;
    /** 指标采集耗时（毫秒）。 */
    private long collectionTimeMs;
    /** 推送延迟（毫秒）。 */
    private long pushDelayMs;
    /** 历史数据占用内存（字节）。 */
    private long historyMemoryBytes;
    /** 推送连接数。 */
    private int pushConnectionCount;
    /** 监控线程池活跃线程数。 */
    private int threadPoolActiveCount;
    /** 监控线程池队列大小。 */
    private int threadPoolQueueSize;
    /** 事件丢弃计数。 */
    private long eventDiscardCount;

    public MonitoringSelfMetrics() {}

    public MonitoringSelfMetrics(long timestamp, long collectionTimeMs, long pushDelayMs,
                                 long historyMemoryBytes, int pushConnectionCount,
                                 int threadPoolActiveCount, int threadPoolQueueSize,
                                 long eventDiscardCount) {
        this.timestamp = timestamp;
        this.collectionTimeMs = collectionTimeMs;
        this.pushDelayMs = pushDelayMs;
        this.historyMemoryBytes = historyMemoryBytes;
        this.pushConnectionCount = pushConnectionCount;
        this.threadPoolActiveCount = threadPoolActiveCount;
        this.threadPoolQueueSize = threadPoolQueueSize;
        this.eventDiscardCount = eventDiscardCount;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public long getCollectionTimeMs() { return collectionTimeMs; }
    public void setCollectionTimeMs(long collectionTimeMs) { this.collectionTimeMs = collectionTimeMs; }
    public long getPushDelayMs() { return pushDelayMs; }
    public void setPushDelayMs(long pushDelayMs) { this.pushDelayMs = pushDelayMs; }
    public long getHistoryMemoryBytes() { return historyMemoryBytes; }
    public void setHistoryMemoryBytes(long historyMemoryBytes) { this.historyMemoryBytes = historyMemoryBytes; }
    public int getPushConnectionCount() { return pushConnectionCount; }
    public void setPushConnectionCount(int pushConnectionCount) { this.pushConnectionCount = pushConnectionCount; }
    public int getThreadPoolActiveCount() { return threadPoolActiveCount; }
    public void setThreadPoolActiveCount(int threadPoolActiveCount) { this.threadPoolActiveCount = threadPoolActiveCount; }
    public int getThreadPoolQueueSize() { return threadPoolQueueSize; }
    public void setThreadPoolQueueSize(int threadPoolQueueSize) { this.threadPoolQueueSize = threadPoolQueueSize; }
    public long getEventDiscardCount() { return eventDiscardCount; }
    public void setEventDiscardCount(long eventDiscardCount) { this.eventDiscardCount = eventDiscardCount; }
}
