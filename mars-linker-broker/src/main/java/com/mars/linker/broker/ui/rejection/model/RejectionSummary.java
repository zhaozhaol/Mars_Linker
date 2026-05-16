package com.mars.linker.broker.ui.rejection.model;

public class RejectionSummary {
    private long connectRefused;
    private long aclSubscribeDenied;
    private long aclPublishDenied;
    private long total;

    public RejectionSummary() {}

    public RejectionSummary(long connectRefused, long aclSubscribeDenied, long aclPublishDenied, long total) {
        this.connectRefused = connectRefused;
        this.aclSubscribeDenied = aclSubscribeDenied;
        this.aclPublishDenied = aclPublishDenied;
        this.total = total;
    }

    public long getConnectRefused() { return connectRefused; }
    public void setConnectRefused(long connectRefused) { this.connectRefused = connectRefused; }
    public long getAclSubscribeDenied() { return aclSubscribeDenied; }
    public void setAclSubscribeDenied(long aclSubscribeDenied) { this.aclSubscribeDenied = aclSubscribeDenied; }
    public long getAclPublishDenied() { return aclPublishDenied; }
    public void setAclPublishDenied(long aclPublishDenied) { this.aclPublishDenied = aclPublishDenied; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
