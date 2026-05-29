package com.mars.linker.broker.netty;

/**
 * 连接关闭原因枚举，用于精确区分断开场景以决定是否发布遗嘱（Will）消息。
 * <p>
 * MQTT 3.1.1 规范要求：遗嘱消息仅在连接异常断开时发布。
 * 以下情况不发布遗嘱：客户端主动 DISCONNECT、被新连接踢掉、服务端 Shutdown。
 * </p>
 * <p>
 * 枚举值分为三组：
 * - 正常断开（shouldPublishWill=false）：客户端主动断开、踢旧连接、服务端关闭、协议拒绝、鉴权失败
 * - 异常断开（shouldPublishWill=true）：IO异常、Keep-Alive超时、协议错误、通道异常
 * - 兜底（shouldPublishWill=false）：未知原因，安全优先不发布
 * </p>
 */
public enum CloseReason {

    CLIENT_DISCONNECT(false, "客户端主动发送 DISCONNECT 报文"),
    KICKED_BY_NEW_CONNECTION(false, "同 clientId 新连接替代，踢掉旧连接"),
    SERVER_SHUTDOWN(false, "服务端正常关闭（@PreDestroy）"),
    PROTOCOL_VIOLATION(false, "协议校验拒绝连接（如无效 CONNECT 报文）"),
    AUTH_FAILED(false, "鉴权失败，连接被拒绝"),
    TOPIC_RATE_LIMITED(false, "精确主题消息速率超限，断开发布者连接"),

    IO_EXCEPTION(true, "网络 IO 异常（连接重置、BrokenPipe 等）"),
    KEEPALIVE_TIMEOUT(true, "Keep-Alive 超时，服务端主动关闭连接"),
    PROTOCOL_ERROR(true, "连接建立后协议处理错误（如非法报文）"),
    CHANNEL_EXCEPTION(true, "Channel 异常（非 IO 的运行时异常）"),

    UNKNOWN(false, "未知原因（兜底，安全优先不发布遗嘱）");

    private final boolean shouldPublishWill;
    private final String description;

    CloseReason(boolean shouldPublishWill, String description) {
        this.shouldPublishWill = shouldPublishWill;
        this.description = description;
    }

    /**
     * 该断开原因是否应发布遗嘱消息。
     *
     * @return true 表示应发布遗嘱，false 表示不发布
     */
    public boolean shouldPublishWill() {
        return shouldPublishWill;
    }

    /**
     * 获取断开原因描述。
     */
    public String getDescription() {
        return description;
    }
}
