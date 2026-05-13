package com.mars.linker.broker.ui.collection;

public class CollectedEvent {

    private final long timestamp;
    private final String type;
    private final String source;
    private final String payload;
    private String status = "pending";
    private Long processedAt;

    public CollectedEvent(long timestamp, String type, String source, String payload) {
        this.timestamp = timestamp;
        this.type = type;
        this.source = source;
        this.payload = payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    public String getSource() {
        return source;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Long processedAt) {
        this.processedAt = processedAt;
    }

    public void markProcessed(String newStatus) {
        this.status = newStatus;
        this.processedAt = System.currentTimeMillis();
    }
}
