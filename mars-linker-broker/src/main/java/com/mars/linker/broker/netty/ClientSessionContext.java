package com.mars.linker.broker.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.Timeout;

/**
 * 聚合连接级会话属性，避免在协议处理器中散落 AttributeKey 读写。
 */
public final class ClientSessionContext {
    private static final AttributeKey<Boolean> CONNECTED = AttributeKey.valueOf("mqtt_connected");
    private static final AttributeKey<String> CLIENT_ID = AttributeKey.valueOf("mqtt_client_id");
    private static final AttributeKey<Integer> PROTOCOL_LEVEL = AttributeKey.valueOf("mqtt_protocol_level");
    private static final AttributeKey<Boolean> CLEAN_SESSION = AttributeKey.valueOf("mqtt_clean_session");
    private static final AttributeKey<Boolean> DISCONNECT_RECEIVED = AttributeKey.valueOf("mqtt_disconnect_received");
    private static final AttributeKey<CloseReason> CLOSE_REASON = AttributeKey.valueOf("mqtt_close_reason");
    private static final AttributeKey<Integer> KEEP_ALIVE_SECONDS = AttributeKey.valueOf("mqtt_keep_alive_seconds");
    private static final AttributeKey<Long> LAST_PACKET_AT_MS = AttributeKey.valueOf("mqtt_last_packet_at_ms");
    private static final AttributeKey<Timeout> KEEPALIVE_TASK = AttributeKey.valueOf("mqtt_keepalive_task");
    private static final AttributeKey<String> WILL_TOPIC = AttributeKey.valueOf("mqtt_will_topic");
    private static final AttributeKey<byte[]> WILL_PAYLOAD = AttributeKey.valueOf("mqtt_will_payload");
    private static final AttributeKey<Integer> WILL_QOS = AttributeKey.valueOf("mqtt_will_qos");
    private static final AttributeKey<Boolean> WILL_RETAIN = AttributeKey.valueOf("mqtt_will_retain");
    private static final AttributeKey<String> TRACE_ID = AttributeKey.valueOf("mqtt_trace_id");

    private final Channel channel;

    private ClientSessionContext(Channel channel) {
        this.channel = channel;
    }

    public static ClientSessionContext of(ChannelHandlerContext ctx) {
        return new ClientSessionContext(ctx.channel());
    }

    public Boolean connected() { return channel.attr(CONNECTED).get(); }
    void connected(Boolean value) { channel.attr(CONNECTED).set(value); }

    public String clientId() { return channel.attr(CLIENT_ID).get(); }
    void clientId(String value) { channel.attr(CLIENT_ID).set(value); }

    public Integer protocolLevel() { return channel.attr(PROTOCOL_LEVEL).get(); }
    void protocolLevel(Integer value) { channel.attr(PROTOCOL_LEVEL).set(value); }

    public Boolean cleanSession() { return channel.attr(CLEAN_SESSION).get(); }
    void cleanSession(Boolean value) { channel.attr(CLEAN_SESSION).set(value); }

    public Boolean disconnectReceived() { return channel.attr(DISCONNECT_RECEIVED).get(); }
    void disconnectReceived(Boolean value) { channel.attr(DISCONNECT_RECEIVED).set(value); }

    public CloseReason closeReason() { return channel.attr(CLOSE_REASON).get(); }
    public void closeReason(CloseReason value) { channel.attr(CLOSE_REASON).set(value); }

    public Integer keepAliveSeconds() { return channel.attr(KEEP_ALIVE_SECONDS).get(); }
    void keepAliveSeconds(Integer value) { channel.attr(KEEP_ALIVE_SECONDS).set(value); }

    public Long lastPacketAtMs() { return channel.attr(LAST_PACKET_AT_MS).get(); }
    void lastPacketAtMs(Long value) { channel.attr(LAST_PACKET_AT_MS).set(value); }

    public Timeout keepAliveTask() { return channel.attr(KEEPALIVE_TASK).get(); }
    void keepAliveTask(Timeout value) { channel.attr(KEEPALIVE_TASK).set(value); }

    String willTopic() { return channel.attr(WILL_TOPIC).get(); }
    void willTopic(String value) { channel.attr(WILL_TOPIC).set(value); }

    byte[] willPayload() { return channel.attr(WILL_PAYLOAD).get(); }
    void willPayload(byte[] value) { channel.attr(WILL_PAYLOAD).set(value); }

    Integer willQos() { return channel.attr(WILL_QOS).get(); }
    void willQos(Integer value) { channel.attr(WILL_QOS).set(value); }

    Boolean willRetain() { return channel.attr(WILL_RETAIN).get(); }
    void willRetain(Boolean value) { channel.attr(WILL_RETAIN).set(value); }

    public String traceId() { return channel.attr(TRACE_ID).get(); }
    void traceId(String value) { channel.attr(TRACE_ID).set(value); }
}
