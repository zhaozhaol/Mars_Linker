package com.mars.linker.broker.ui.monitoring.model;

import java.util.List;

public class ClientSubscriptionInfo {
    private String clientId;
    private List<SubscriptionTopicInfo> subscriptions;

    public ClientSubscriptionInfo() {}

    public ClientSubscriptionInfo(String clientId, List<SubscriptionTopicInfo> subscriptions) {
        this.clientId = clientId;
        this.subscriptions = subscriptions;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public List<SubscriptionTopicInfo> getSubscriptions() { return subscriptions; }
    public void setSubscriptions(List<SubscriptionTopicInfo> subscriptions) { this.subscriptions = subscriptions; }
}
