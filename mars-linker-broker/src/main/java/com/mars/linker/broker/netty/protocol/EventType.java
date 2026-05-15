package com.mars.linker.broker.netty.protocol;

/**
 * Broker 事件类型枚举，定义了 Broker 可产生的所有事件类别。
 * <p>
 * 每种事件类型关联默认转发主题和默认 reason，用于未配置自定义值时的兜底。
 * 枚举不可扩展，新增事件类型需修改枚举定义并重新编译。
 * </p>
 */
public enum EventType {

    CONNECTED("connected", "devices/connected", "connect"),
    CONNECT_REFUSED("connect_refused", "devices/connect-refused", null),
    DISCONNECTED("disconnected", "devices/disconnected", null),
    SUBSCRIBED("subscribed", "devices/subscribed", "subscribe"),
    UNSUBSCRIBED("unsubscribed", "devices/unsubscribed", "unsubscribe"),
    SESSION_CREATED("session_created", "devices/session-created", "session_create"),
    SESSION_DESTROYED("session_destroyed", "devices/session-destroyed", "session_destroy"),
    WILL_PUBLISHED("will_published", "devices/will-published", "will_publish"),
    CONNECTION_KICKED("connection_kicked", "devices/connection-kicked", "kicked_by_new_connection");

    private final String value;
    private final String defaultTopic;
    private final String defaultReason;

    EventType(String value, String defaultTopic, String defaultReason) {
        this.value = value;
        this.defaultTopic = defaultTopic;
        this.defaultReason = defaultReason;
    }

    public String getValue() {
        return value;
    }

    public String getDefaultTopic() {
        return defaultTopic;
    }

    public String getDefaultReason() {
        return defaultReason;
    }

    /**
     * 根据 YAML 配置键名解析为枚举值。
     * 支持 Spring Boot 宽松绑定格式：connect-refused → CONNECT_REFUSED。
     *
     * @param configKey 配置键名（如 "connected"、"connect-refused"）
     * @return 对应的 EventType 枚举值
     * @throws IllegalArgumentException 不支持的事件类型名称时抛出
     */
    public static EventType fromConfigKey(String configKey) {
        if (configKey == null || configKey.isEmpty()) {
            throw new IllegalArgumentException("事件类型配置键不能为空");
        }
        String enumName = configKey.replace('-', '_').toUpperCase();
        try {
            return valueOf(enumName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的事件类型: " + configKey
                    + "，支持的事件类型: connected, connect-refused, disconnected, subscribed, "
                    + "unsubscribed, session-created, session-destroyed, will-published, connection-kicked");
        }
    }
}
