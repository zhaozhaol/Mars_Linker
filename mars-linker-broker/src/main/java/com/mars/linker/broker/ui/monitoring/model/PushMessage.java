package com.mars.linker.broker.ui.monitoring.model;

/**
 * 推送消息封装。
 */
public class PushMessage {

    /** 消息类型。 */
    private String type;
    /** 指标类别。 */
    private String category;
    /** 消息时间戳（毫秒）。 */
    private long timestamp;
    /** 消息载荷。 */
    private Object payload;

    public PushMessage() {}

    public PushMessage(String type, String category, long timestamp, Object payload) {
        this.type = type;
        this.category = category;
        this.timestamp = timestamp;
        this.payload = payload;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
