package com.mars.linker.broker.ui.monitoring.model;

public class SubscriberDetail {
    private String clientId;
    private int grantedQos;
    private int protocolLevel;
    private long connectedDurationMs;
    private String remoteAddress;

    public SubscriberDetail() {}

    public SubscriberDetail(String clientId, int grantedQos, int protocolLevel, long connectedDurationMs, String remoteAddress) {
        this.clientId = clientId;
        this.grantedQos = grantedQos;
        this.protocolLevel = protocolLevel;
        this.connectedDurationMs = connectedDurationMs;
        this.remoteAddress = remoteAddress;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public int getGrantedQos() { return grantedQos; }
    public void setGrantedQos(int grantedQos) { this.grantedQos = grantedQos; }
    public int getProtocolLevel() { return protocolLevel; }
    public void setProtocolLevel(int protocolLevel) { this.protocolLevel = protocolLevel; }
    public long getConnectedDurationMs() { return connectedDurationMs; }
    public void setConnectedDurationMs(long connectedDurationMs) { this.connectedDurationMs = connectedDurationMs; }
    public String getRemoteAddress() { return remoteAddress; }
    public void setRemoteAddress(String remoteAddress) { this.remoteAddress = remoteAddress; }
}
