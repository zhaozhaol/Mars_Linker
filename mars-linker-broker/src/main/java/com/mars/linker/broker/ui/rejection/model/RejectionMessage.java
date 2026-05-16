package com.mars.linker.broker.ui.rejection.model;

public class RejectionMessage {
    private long id;
    private long timestamp;
    private String clientId;
    private RejectionType type;
    private RejectionReason reason;
    private String remoteAddress;
    private String detail;
    private Integer connackCode;

    public RejectionMessage() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public RejectionType getType() { return type; }
    public void setType(RejectionType type) { this.type = type; }
    public RejectionReason getReason() { return reason; }
    public void setReason(RejectionReason reason) { this.reason = reason; }
    public String getRemoteAddress() { return remoteAddress; }
    public void setRemoteAddress(String remoteAddress) { this.remoteAddress = remoteAddress; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Integer getConnackCode() { return connackCode; }
    public void setConnackCode(Integer connackCode) { this.connackCode = connackCode; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final RejectionMessage msg = new RejectionMessage();

        public Builder id(long id) { msg.id = id; return this; }
        public Builder timestamp(long timestamp) { msg.timestamp = timestamp; return this; }
        public Builder clientId(String clientId) { msg.clientId = clientId; return this; }
        public Builder type(RejectionType type) { msg.type = type; return this; }
        public Builder reason(RejectionReason reason) { msg.reason = reason; return this; }
        public Builder remoteAddress(String remoteAddress) { msg.remoteAddress = remoteAddress; return this; }
        public Builder detail(String detail) { msg.detail = detail; return this; }
        public Builder connackCode(Integer connackCode) { msg.connackCode = connackCode; return this; }
        public RejectionMessage build() { return msg; }
    }
}
