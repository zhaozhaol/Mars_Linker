package com.mars.linker.broker.ui.collection;

/**
 * UI 数据采集事件。
 */
public class CollectedEvent {

    private final long timestamp;
    private final String type;
    private final String source;
    private final String payload;

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
}
