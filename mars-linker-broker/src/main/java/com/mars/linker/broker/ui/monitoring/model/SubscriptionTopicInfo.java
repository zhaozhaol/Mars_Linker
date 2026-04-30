package com.mars.linker.broker.ui.monitoring.model;

public class SubscriptionTopicInfo {
    private String topicFilter;
    private int subscriberCount;
    private String subscriptionType;

    public SubscriptionTopicInfo() {}

    public SubscriptionTopicInfo(String topicFilter, int subscriberCount, String subscriptionType) {
        this.topicFilter = topicFilter;
        this.subscriberCount = subscriberCount;
        this.subscriptionType = subscriptionType;
    }

    public String getTopicFilter() { return topicFilter; }
    public void setTopicFilter(String topicFilter) { this.topicFilter = topicFilter; }
    public int getSubscriberCount() { return subscriberCount; }
    public void setSubscriberCount(int subscriberCount) { this.subscriberCount = subscriberCount; }
    public String getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(String subscriptionType) { this.subscriptionType = subscriptionType; }
}
