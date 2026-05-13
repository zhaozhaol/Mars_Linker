package com.mars.linker.broker.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongConsumer;

/**
 * QoS2 上行：pending/completed 窗口管理与握手状态迁移。
 */
public final class QoS2InboundService {
    private static final AttributeKey<ConcurrentHashMap<Integer, PendingMessage>> INBOUND_QOS2_PENDING =
            AttributeKey.valueOf("mqtt_inbound_qos2_pending");
    private static final AttributeKey<Set<Integer>> INBOUND_QOS2_COMPLETED =
            AttributeKey.valueOf("mqtt_inbound_qos2_completed");
    private static final AttributeKey<Queue<Integer>> INBOUND_QOS2_COMPLETED_ORDER =
            AttributeKey.valueOf("mqtt_inbound_qos2_completed_order");
    private static final int INBOUND_QOS2_COMPLETED_WINDOW = 1024;

    private final InboundPublishDispatcher inboundPublishDispatcher;
    private final LongConsumer pendingDeltaRecorder;
    private final Runnable completedRecorder;
    private final int maxPendingPerConnection;
    private final Logger log;

    public QoS2InboundService(InboundPublishDispatcher inboundPublishDispatcher,
                       LongConsumer pendingDeltaRecorder,
                       Runnable completedRecorder,
                       int maxPendingPerConnection,
                       Logger log) {
        this.inboundPublishDispatcher = inboundPublishDispatcher;
        this.pendingDeltaRecorder = pendingDeltaRecorder;
        this.completedRecorder = completedRecorder;
        this.maxPendingPerConnection = maxPendingPerConnection;
        this.log = log;
    }

    public void onInboundQos2Publish(ChannelHandlerContext ctx,
                              int packetId,
                              String topic,
                              byte[] payload,
                              boolean retain,
                              boolean dup,
                              int qos) {
        if (isCompleted(ctx, packetId)) {
            writePubRec(ctx, packetId);
            log.debug("上行 QoS2 重复 PUBLISH 命中去重窗口 packetId={} channelId={}",
                    packetId, ctx.channel().id().asShortText());
            return;
        }
        if (!trackPending(ctx, packetId, topic, payload, retain, dup, qos)) {
            return;
        }
        writePubRec(ctx, packetId);
        log.debug("上行 QoS2 PUBLISH 已接收，等待 PUBREL topic={} packetId={} channelId={}",
                topic, packetId, ctx.channel().id().asShortText());
    }

    public void onPubRel(ChannelHandlerContext ctx, int packetId) {
        ConcurrentHashMap<Integer, PendingMessage> pending = ctx.channel().attr(INBOUND_QOS2_PENDING).get();
        PendingMessage msg = pending == null ? null : pending.remove(packetId);
        if (msg != null) {
            inboundPublishDispatcher.dispatch(ctx, msg.topic, msg.payload, msg.retain, msg.dup, 1);
            markCompleted(ctx, packetId);
            log.debug("PUBREL 已处理并投递 topic={} packetId={} channelId={}",
                    msg.topic, packetId, ctx.channel().id().asShortText());
        } else {
            log.warn("PUBREL packetId={} 无对应 pending 消息，协议违规关闭连接 channelId={}",
                    packetId, ctx.channel().id().asShortText());
            ctx.close();
            return;
        }
        writePubComp(ctx, packetId);
    }

    public void onChannelInactive(ChannelHandlerContext ctx) {
        ConcurrentHashMap<Integer, PendingMessage> pending = ctx.channel().attr(INBOUND_QOS2_PENDING).get();
        if (pending != null && !pending.isEmpty()) {
            pendingDeltaRecorder.accept(-pending.size());
            pending.clear();
        }
        Set<Integer> completed = ctx.channel().attr(INBOUND_QOS2_COMPLETED).get();
        if (completed != null) {
            completed.clear();
        }
        Queue<Integer> completedOrder = ctx.channel().attr(INBOUND_QOS2_COMPLETED_ORDER).get();
        if (completedOrder != null) {
            completedOrder.clear();
        }
    }

    private boolean trackPending(ChannelHandlerContext ctx,
                                 int packetId,
                                 String topic,
                                 byte[] payload,
                                 boolean retain,
                                 boolean dup,
                                 int qos) {
        ConcurrentHashMap<Integer, PendingMessage> pending = ctx.channel().attr(INBOUND_QOS2_PENDING).get();
        if (pending == null) {
            pending = new ConcurrentHashMap<>();
            ctx.channel().attr(INBOUND_QOS2_PENDING).set(pending);
        }
        if (!pending.containsKey(packetId)
                && maxPendingPerConnection > 0
                && pending.size() >= maxPendingPerConnection) {
            log.warn("上行 QoS2 pending 超限，关闭连接 packetId={} maxPending={} channelId={}",
                    packetId, maxPendingPerConnection, ctx.channel().id().asShortText());
            ctx.close();
            return false;
        }
        byte[] copy = new byte[payload.length];
        System.arraycopy(payload, 0, copy, 0, payload.length);
        PendingMessage prev = pending.put(packetId, new PendingMessage(topic, copy, retain, dup, qos));
        if (prev == null) {
            pendingDeltaRecorder.accept(1);
        }
        return true;
    }

    private boolean isCompleted(ChannelHandlerContext ctx, int packetId) {
        Set<Integer> completed = ctx.channel().attr(INBOUND_QOS2_COMPLETED).get();
        return completed != null && completed.contains(packetId);
    }

    private void markCompleted(ChannelHandlerContext ctx, int packetId) {
        Set<Integer> completed = ctx.channel().attr(INBOUND_QOS2_COMPLETED).get();
        if (completed == null) {
            completed = ConcurrentHashMap.newKeySet();
            ctx.channel().attr(INBOUND_QOS2_COMPLETED).set(completed);
        }
        Queue<Integer> order = ctx.channel().attr(INBOUND_QOS2_COMPLETED_ORDER).get();
        if (order == null) {
            order = new ConcurrentLinkedQueue<>();
            ctx.channel().attr(INBOUND_QOS2_COMPLETED_ORDER).set(order);
        }
        if (completed.add(packetId)) {
            order.add(packetId);
            completedRecorder.run();
            pendingDeltaRecorder.accept(-1);
        }
        while (completed.size() > INBOUND_QOS2_COMPLETED_WINDOW) {
            Integer oldest = order.poll();
            if (oldest == null) {
                break;
            }
            completed.remove(oldest);
        }
    }

    private static void writePubRec(ChannelHandlerContext ctx, int packetId) {
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeByte(0x50);
        buf.writeByte(0x02);
        buf.writeShort(packetId);
        ctx.writeAndFlush(buf);
    }

    private static void writePubComp(ChannelHandlerContext ctx, int packetId) {
        ByteBuf buf = Unpooled.buffer(4);
        buf.writeByte(0x70);
        buf.writeByte(0x02);
        buf.writeShort(packetId);
        ctx.writeAndFlush(buf);
    }

    @FunctionalInterface
    public interface InboundPublishDispatcher {
        public int dispatch(ChannelHandlerContext ctx, String topic, byte[] payload, boolean retain, boolean dup, int qos);
    }

    private static final class PendingMessage {
        final String topic;
        final byte[] payload;
        final boolean retain;
        final boolean dup;
        final int qos;

        private PendingMessage(String topic, byte[] payload, boolean retain, boolean dup, int qos) {
            this.topic = topic;
            this.payload = payload;
            this.retain = retain;
            this.dup = dup;
            this.qos = qos;
        }
    }
}
