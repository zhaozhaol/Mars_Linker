package com.mars.linker.broker.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import com.mars.linker.broker.netty.trace.TraceContext;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

/**
 * QoS1 下行：packetId 分配、inflight 跟踪与可选重传（DUP=1）。
 * <p>
 * OPT-9: 移除冗余 {@code Set<Integer>} inflight 结构，统一用
 * {@code ConcurrentHashMap<Integer, Inflight>} 做跟踪。重传未启用时用
 * {@link #NO_RETRANSMIT_MARKER} 占位，避免创建完整 Inflight 对象。
 */
public final class QoS1OutboundService {
    private static final AttributeKey<Integer> PROTOCOL_LEVEL = AttributeKey.valueOf("mqtt_protocol_level");

    private static final AttributeKey<ConcurrentHashMap<Integer, Inflight>> OUTBOUND_QOS1_INFLIGHT_MAP =
            AttributeKey.valueOf("mqtt_outbound_qos1_inflight_map");
    private static final AttributeKey<Timeout> OUTBOUND_QOS1_RETRANSMIT_TASK =
            AttributeKey.valueOf("mqtt_outbound_qos1_retransmit_task");
    private static final AttributeKey<AtomicInteger> NEXT_OUTBOUND_PACKET_ID =
            AttributeKey.valueOf("mqtt_next_outbound_packet_id");
    // BUG-11: 共享 packetId 空间，回绕时需同时检查 QoS2 下行 inflight（与 QoS2OutboundService 同名 key 共享）
    @SuppressWarnings("unchecked")
    private static final AttributeKey<ConcurrentHashMap<Integer, ?>> OUTBOUND_QOS2_INFLIGHT_CHECK =
            AttributeKey.valueOf("mqtt_outbound_qos2_inflight");

    /** 重传未启用时的占位标记，所有非重传 inflight 条目共享此对象。 */
    private static final Inflight NO_RETRANSMIT_MARKER = new Inflight(null, null, false, 0L);

    private final boolean retransmitEnabled;
    private final long retransmitIntervalMs;
    private final int retransmitMaxAttempts;
    private final Logger log;
    private final HashedWheelTimer retransmitTimer;

    public QoS1OutboundService(boolean retransmitEnabled,
                        long retransmitIntervalMs,
                        int retransmitMaxAttempts,
                        Logger log,
                        HashedWheelTimer retransmitTimer) {
        this.retransmitEnabled = retransmitEnabled;
        this.retransmitIntervalMs = retransmitIntervalMs;
        this.retransmitMaxAttempts = retransmitMaxAttempts;
        this.log = log;
        this.retransmitTimer = retransmitTimer;
    }

    public void onChannelActive(ChannelHandlerContext ctx) {
        startRetransmitTaskIfNeeded(ctx);
    }

    public void onChannelInactive(ChannelHandlerContext ctx) {
        stopRetransmitTask(ctx);
    }

    public int nextPacketId(ChannelHandlerContext ctx) {
        AtomicInteger id = ctx.channel().attr(NEXT_OUTBOUND_PACKET_ID).get();
        if (id == null) {
            AtomicInteger newId = new AtomicInteger(0);
            id = ctx.channel().attr(NEXT_OUTBOUND_PACKET_ID).compareAndSet(null, newId) ? newId : ctx.channel().attr(NEXT_OUTBOUND_PACKET_ID).get();
        }
        ConcurrentHashMap<Integer, Inflight> qos1Map = ctx.channel().attr(OUTBOUND_QOS1_INFLIGHT_MAP).get();
        ConcurrentHashMap<Integer, ?> qos2Inflight = ctx.channel().attr(OUTBOUND_QOS2_INFLIGHT_CHECK).get();
        return id.updateAndGet(prev -> {
            int n = prev + 1;
            if (n > 0xFFFF) {
                n = 1;
            }
            // BUG-11 修复：回绕时跳过仍在 inflight 的 packetId（QoS1 + QoS2 共享 packetId 空间）
            if ((qos1Map != null && qos1Map.containsKey(n))
                    || (qos2Inflight != null && qos2Inflight.containsKey(n))) {
                int start = n;
                do {
                    n = (n % 0xFFFF) + 1;
                    if (n == start) {
                        return prev; // 所有 id 都在 inflight（不应发生），保持原值
                    }
                } while ((qos1Map != null && qos1Map.containsKey(n))
                        || (qos2Inflight != null && qos2Inflight.containsKey(n)));
            }
            return n;
        });
    }

    public void track(ChannelHandlerContext ctx,
               int packetId,
               String topic,
               byte[] payload,
               boolean retain) {
        // OPT-9: 统一用 inflightMap 跟踪，移除冗余 Set
        ConcurrentHashMap<Integer, Inflight> m = getOrCreateInflightMap(ctx);
        if (!retransmitEnabled || retransmitIntervalMs <= 0) {
            m.put(packetId, NO_RETRANSMIT_MARKER);
            return;
        }
        byte[] copy = new byte[payload.length];
        System.arraycopy(payload, 0, copy, 0, payload.length);
        m.put(packetId, new Inflight(topic, copy, retain));
    }

    public void onPubAck(ChannelHandlerContext ctx, int packetId) {
        ConcurrentHashMap<Integer, Inflight> m = ctx.channel().attr(OUTBOUND_QOS1_INFLIGHT_MAP).get();
        if (m != null) {
            m.remove(packetId);
        }
    }

    public void sendPublish(ChannelHandlerContext ctx,
                     String topic,
                     int packetId,
                     byte[] payload,
                     boolean retain,
                     boolean dup) {
        byte[] topicBytes = TopicEncodingCache.get(topic);
        Integer level = ctx.channel().attr(PROTOCOL_LEVEL).get();
        boolean mqtt5 = level != null && level == 0x05;
        int rl = 2 + topicBytes.length + 2 + (mqtt5 ? 1 : 0) + payload.length;
        ByteBuf out = ctx.alloc().buffer(5 + rl);
        int fh = 0x32 | (dup ? 0x08 : 0x00) | (retain ? 0x01 : 0x00);
        out.writeByte(fh);
        writeRemainingLength(out, rl);
        out.writeShort(topicBytes.length);
        out.writeBytes(topicBytes);
        out.writeShort(packetId);
        if (mqtt5) {
            out.writeByte(0x00); // properties length
        }
        out.writeBytes(payload);
        ctx.writeAndFlush(out);
    }

    private void startRetransmitTaskIfNeeded(ChannelHandlerContext ctx) {
        if (!retransmitEnabled) {
            return;
        }
        if (retransmitIntervalMs <= 0) {
            return;
        }
        if (ctx.channel().attr(OUTBOUND_QOS1_RETRANSMIT_TASK).get() != null) {
            return;
        }
        Timeout t = retransmitTimer.newTimeout(
                timeout -> {
                    String tid = TraceContext.getTraceId(ctx);
                    TraceContext.setTraceId(tid);
                    try {
                    if (timeout.isExpired()) {
                        retransmitIfNeeded(ctx);
                    }
                    if (ctx.channel().isActive()) {
                        startRetransmitTaskIfNeeded(ctx);
                    }
                    } finally {
                        TraceContext.clearTraceId();
                    }
                },
                retransmitIntervalMs, TimeUnit.MILLISECONDS
        );
        ctx.channel().attr(OUTBOUND_QOS1_RETRANSMIT_TASK).set(t);
    }

    private void stopRetransmitTask(ChannelHandlerContext ctx) {
        Timeout t = ctx.channel().attr(OUTBOUND_QOS1_RETRANSMIT_TASK).get();
        if (t != null) {
            t.cancel();
            ctx.channel().attr(OUTBOUND_QOS1_RETRANSMIT_TASK).set(null);
        }
    }

    private void retransmitIfNeeded(ChannelHandlerContext ctx) {
        if (!ctx.channel().isActive()) {
            return;
        }
        ConcurrentHashMap<Integer, Inflight> m = ctx.channel().attr(OUTBOUND_QOS1_INFLIGHT_MAP).get();
        if (m == null || m.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Inflight> e : m.entrySet()) {
            int packetId = e.getKey();
            Inflight msg = e.getValue();
            if (msg == null || msg.topic == null) {
                // OPT-9: NO_RETRANSMIT_MARKER 或 null 跳过
                continue;
            }
            long age = now - msg.lastSentAtMs;
            if (age < retransmitIntervalMs) {
                continue;
            }
            if (msg.retransmitAttempts >= retransmitMaxAttempts) {
                continue;
            }
            msg.retransmitAttempts++;
            msg.lastSentAtMs = now;
            sendPublish(ctx, msg.topic, packetId, msg.payload, msg.retain, true);
            if (log != null) {
                log.debug("下行 QoS1 重传 topic={} packetId={} attempt={}/{} channelId={}",
                        msg.topic, packetId, msg.retransmitAttempts, retransmitMaxAttempts, ctx.channel().id().asShortText());
            }
        }
    }

    // OPT-9: 移除 trackInflightId，统一用 getOrCreateInflightMap
    private static ConcurrentHashMap<Integer, Inflight> getOrCreateInflightMap(ChannelHandlerContext ctx) {
        // BUG-5 修复：用 compareAndSet 原子化，避免跨 EventLoop 并发时两个发布者都看到 null 导致 set 互相覆盖
        ConcurrentHashMap<Integer, Inflight> m = ctx.channel().attr(OUTBOUND_QOS1_INFLIGHT_MAP).get();
        if (m == null) {
            m = new ConcurrentHashMap<>();
            if (!ctx.channel().attr(OUTBOUND_QOS1_INFLIGHT_MAP).compareAndSet(null, m)) {
                m = ctx.channel().attr(OUTBOUND_QOS1_INFLIGHT_MAP).get();
            }
        }
        return m;
    }

    private static void writeRemainingLength(ByteBuf out, int value) {
        int x = value;
        do {
            int encodedByte = x % 128;
            x = x / 128;
            if (x > 0) {
                encodedByte = encodedByte | 0x80;
            }
            out.writeByte(encodedByte);
        } while (x > 0);
    }

    private static final class Inflight {
        final String topic;
        final byte[] payload;
        final boolean retain;
        volatile long lastSentAtMs;
        volatile int retransmitAttempts;

        private Inflight(String topic, byte[] payload, boolean retain) {
            this.topic = topic;
            this.payload = payload;
            this.retain = retain;
            this.lastSentAtMs = System.currentTimeMillis();
            this.retransmitAttempts = 0;
        }

        /** 用于创建 NO_RETRANSMIT_MARKER，跳过 System.currentTimeMillis() 调用。 */
        private Inflight(String topic, byte[] payload, boolean retain, long lastSentAtMs) {
            this.topic = topic;
            this.payload = payload;
            this.retain = retain;
            this.lastSentAtMs = lastSentAtMs;
            this.retransmitAttempts = 0;
        }
    }
}
