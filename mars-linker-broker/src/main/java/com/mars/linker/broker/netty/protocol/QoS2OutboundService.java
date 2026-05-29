package com.mars.linker.broker.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * QoS2 下行：PUBLISH→PUBREC→PUBREL→PUBCOMP 握手与可选重传。
 */
public final class QoS2OutboundService {
    private static final AttributeKey<ConcurrentHashMap<Integer, InflightQos2>> OUTBOUND_QOS2_INFLIGHT =
            AttributeKey.valueOf("mqtt_outbound_qos2_inflight");
    private static final AttributeKey<Timeout> OUTBOUND_QOS2_RETRANSMIT_TASK =
            AttributeKey.valueOf("mqtt_outbound_qos2_retransmit_task");

    private final boolean retransmitEnabled;
    private final long retransmitIntervalMs;
    private final int retransmitMaxAttempts;
    private final QoS1OutboundService qos1Outbound;
    private final Logger log;
    private final HashedWheelTimer retransmitTimer;

    public QoS2OutboundService(boolean retransmitEnabled,
                                long retransmitIntervalMs,
                                int retransmitMaxAttempts,
                                QoS1OutboundService qos1Outbound,
                                Logger log,
                                HashedWheelTimer retransmitTimer) {
        this.retransmitEnabled = retransmitEnabled;
        this.retransmitIntervalMs = retransmitIntervalMs;
        this.retransmitMaxAttempts = retransmitMaxAttempts;
        this.qos1Outbound = qos1Outbound;
        this.log = log;
        this.retransmitTimer = retransmitTimer;
    }

    public void onChannelActive(ChannelHandlerContext ctx) {
        startRetransmitTaskIfNeeded(ctx);
    }

    public void onChannelInactive(ChannelHandlerContext ctx) {
        stopRetransmitTask(ctx);
    }

    public void publishQos2(ChannelHandlerContext ctx,
                            String topic,
                            byte[] payload,
                            boolean retain,
                            int packetId) {
        trackInflight(ctx, packetId, topic, payload, retain, State.PUBLISHED);
        qos1Outbound.sendPublish(ctx, topic, packetId, payload, retain, false);
    }

    public void onPubRec(ChannelHandlerContext ctx, int packetId) {
        ConcurrentHashMap<Integer, InflightQos2> m = ctx.channel().attr(OUTBOUND_QOS2_INFLIGHT).get();
        if (m == null) {
            return;
        }
        InflightQos2 inflight = m.get(packetId);
        if (inflight == null) {
            writePubRel(ctx, packetId);
            return;
        }
        if (inflight.state == State.PUBLISHED) {
            inflight.state = State.PUBREC_RECEIVED;
            inflight.lastSentAtMs = System.currentTimeMillis();
        }
        writePubRel(ctx, packetId);
    }

    public void onPubComp(ChannelHandlerContext ctx, int packetId) {
        ConcurrentHashMap<Integer, InflightQos2> m = ctx.channel().attr(OUTBOUND_QOS2_INFLIGHT).get();
        if (m != null) {
            m.remove(packetId);
        }
    }

    private void trackInflight(ChannelHandlerContext ctx,
                               int packetId,
                               String topic,
                               byte[] payload,
                               boolean retain,
                               State initialState) {
        ConcurrentHashMap<Integer, InflightQos2> m = ctx.channel().attr(OUTBOUND_QOS2_INFLIGHT).get();
        if (m == null) {
            m = new ConcurrentHashMap<>();
            ctx.channel().attr(OUTBOUND_QOS2_INFLIGHT).set(m);
        }
        byte[] copy = new byte[payload.length];
        System.arraycopy(payload, 0, copy, 0, payload.length);
        m.put(packetId, new InflightQos2(topic, copy, retain, initialState));
    }

    private static void writePubRel(ChannelHandlerContext ctx, int packetId) {
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeByte(0x62);
        buf.writeByte(0x02);
        buf.writeShort(packetId);
        ctx.writeAndFlush(buf);
    }

    private void startRetransmitTaskIfNeeded(ChannelHandlerContext ctx) {
        if (!retransmitEnabled || retransmitIntervalMs <= 0) {
            return;
        }
        if (ctx.channel().attr(OUTBOUND_QOS2_RETRANSMIT_TASK).get() != null) {
            return;
        }
        Timeout t = retransmitTimer.newTimeout(
                timeout -> {
                    if (timeout.isExpired()) {
                        retransmitIfNeeded(ctx);
                    }
                    if (ctx.channel().isActive()) {
                        startRetransmitTaskIfNeeded(ctx);
                    }
                },
                retransmitIntervalMs, TimeUnit.MILLISECONDS
        );
        ctx.channel().attr(OUTBOUND_QOS2_RETRANSMIT_TASK).set(t);
    }

    private void stopRetransmitTask(ChannelHandlerContext ctx) {
        Timeout t = ctx.channel().attr(OUTBOUND_QOS2_RETRANSMIT_TASK).get();
        if (t != null) {
            t.cancel();
            ctx.channel().attr(OUTBOUND_QOS2_RETRANSMIT_TASK).set(null);
        }
    }

    private void retransmitIfNeeded(ChannelHandlerContext ctx) {
        if (!ctx.channel().isActive()) {
            return;
        }
        ConcurrentHashMap<Integer, InflightQos2> m = ctx.channel().attr(OUTBOUND_QOS2_INFLIGHT).get();
        if (m == null || m.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, InflightQos2> e : m.entrySet()) {
            int packetId = e.getKey();
            InflightQos2 inflight = e.getValue();
            if (inflight == null) {
                continue;
            }
            long age = now - inflight.lastSentAtMs;
            if (age < retransmitIntervalMs) {
                continue;
            }
            if (inflight.retransmitAttempts >= retransmitMaxAttempts) {
                continue;
            }
            inflight.retransmitAttempts++;
            inflight.lastSentAtMs = now;
            if (inflight.state == State.PUBLISHED) {
                qos1Outbound.sendPublish(ctx, inflight.topic, packetId, inflight.payload, inflight.retain, true);
                if (log != null) {
                    log.debug("下行 QoS2 重传 PUBLISH topic={} packetId={} attempt={} channelId={}",
                            inflight.topic, packetId, inflight.retransmitAttempts, ctx.channel().id().asShortText());
                }
            } else if (inflight.state == State.PUBREC_RECEIVED) {
                writePubRel(ctx, packetId);
                if (log != null) {
                    log.debug("下行 QoS2 重传 PUBREL packetId={} attempt={} channelId={}",
                            packetId, inflight.retransmitAttempts, ctx.channel().id().asShortText());
                }
            }
        }
    }

    private enum State {
        PUBLISHED,
        PUBREC_RECEIVED
    }

    private static final class InflightQos2 {
        final String topic;
        final byte[] payload;
        final boolean retain;
        volatile State state;
        volatile long lastSentAtMs;
        volatile int retransmitAttempts;

        private InflightQos2(String topic, byte[] payload, boolean retain, State state) {
            this.topic = topic;
            this.payload = payload;
            this.retain = retain;
            this.state = state;
            this.lastSentAtMs = System.currentTimeMillis();
            this.retransmitAttempts = 0;
        }
    }
}
