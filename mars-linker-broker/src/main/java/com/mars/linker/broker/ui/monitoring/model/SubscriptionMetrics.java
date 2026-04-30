package com.mars.linker.broker.ui.monitoring.model;

/**
 * 订阅指标数据。
 */
public class SubscriptionMetrics {

    /** 采集时间戳（毫秒）。 */
    private long timestamp;
    /** 订阅总数。 */
    private long subscriptionsTotal;
    /** Topic 数量。 */
    private long topicCount;
    /** 订阅树深度。 */
    private int subscriptionTreeDepth;
    /** 是否处于降级状态。 */
    private boolean degraded;

    public SubscriptionMetrics() {}

    public SubscriptionMetrics(long timestamp, long subscriptionsTotal, long topicCount,
                               int subscriptionTreeDepth, boolean degraded) {
        this.timestamp = timestamp;
        this.subscriptionsTotal = subscriptionsTotal;
        this.topicCount = topicCount;
        this.subscriptionTreeDepth = subscriptionTreeDepth;
        this.degraded = degraded;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public long getSubscriptionsTotal() { return subscriptionsTotal; }
    public void setSubscriptionsTotal(long subscriptionsTotal) { this.subscriptionsTotal = subscriptionsTotal; }
    public long getTopicCount() { return topicCount; }
    public void setTopicCount(long topicCount) { this.topicCount = topicCount; }
    public int getSubscriptionTreeDepth() { return subscriptionTreeDepth; }
    public void setSubscriptionTreeDepth(int subscriptionTreeDepth) { this.subscriptionTreeDepth = subscriptionTreeDepth; }
    public boolean isDegraded() { return degraded; }
    public void setDegraded(boolean degraded) { this.degraded = degraded; }
}
