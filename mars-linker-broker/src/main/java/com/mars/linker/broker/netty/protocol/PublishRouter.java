package com.mars.linker.broker.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;

import java.util.Map;
import com.mars.linker.broker.netty.store.SessionService;

/**
 * 发布路由与下行投递（QoS0/1）处理。
 * <p>
 * OPT-8: payload 参数改为 ByteBuf，QoS0/QoS1 转发路径使用
 * {@code out.writeBytes(payload, readerIndex, len)} 零拷贝写入，
 * 避免中间 byte[] 分配。byte[] 仅在需要存储时（离线队列、QoS1 inflight、QoS2 inflight）按需提取。
 * <b>调用方负责 ByteBuf 的 release，本方法不 release。</b>
 */
public final class PublishRouter {
    private static final AttributeKey<Integer> PROTOCOL_LEVEL = AttributeKey.valueOf("mqtt_protocol_level");

    public interface PacketIdSupplier {
        public int next(ChannelHandlerContext ctx);
    }

    public interface OutboundTracker {
        public void track(ChannelHandlerContext ctx, int packetId, String topic, byte[] payload, boolean retain);
    }

    public interface QoS2OutboundPublisher {
        public void publishQos2(ChannelHandlerContext ctx, String topic, byte[] payload, boolean retain, int packetId);
    }

    private PublishRouter() {
    }

    public static int publishAndEnqueueOffline(String topic,
                                        ByteBuf payload,
                                        boolean retain,
                                        boolean dup,
                                        int pubQos,
                                        SubscriptionRegistry subscriptionRegistry,
                                        Map<ChannelId, ChannelHandlerContext> channels,
                                        SessionService sessionService,
                                        Map<String, ChannelId> clientToChannel,
                                        SubscriptionRegistry.GrantedQosLookup grantedQosLookup,
                                        PacketIdSupplier packetIdSupplier,
                                        OutboundTracker outboundTracker,
                                        QoS2OutboundPublisher qos2Publisher,
                                        Logger log) {
        Map<ChannelId, Integer> grantedQosBySubscriber = subscriptionRegistry.collectGrantedQos(
                topic,
                grantedQosLookup,
                channelId -> {
                    ChannelHandlerContext c = channels.get(channelId);
                    return c != null && c.channel().isActive();
                }
        );

        int payloadLen = payload.readableBytes();
        int payloadReaderIndex = payload.readerIndex();

        if (grantedQosBySubscriber.isEmpty()) {
            byte[] bytes = new byte[payloadLen];
            payload.getBytes(payloadReaderIndex, bytes);
            sessionService.enqueueForOfflineMatches(topic, bytes, false, pubQos);
            log.trace("PUBLISH 无订阅者 topic={}", topic);
            return 0;
        }

        byte[] topicBytes = TopicEncodingCache.get(topic);
        byte[] payloadBytes = null; // 懒提取：仅 QoS1/QoS2 inflight 和离线队列需要 byte[]
        int n = 0;
        for (Map.Entry<ChannelId, Integer> entry : grantedQosBySubscriber.entrySet()) {
            ChannelId subscriberId = entry.getKey();
            ChannelHandlerContext subscriberCtx = channels.get(subscriberId);
            if (subscriberCtx == null || !subscriberCtx.channel().isActive()) {
                log.trace("跳过无效订阅者 channelId={} topic={}", subscriberId, topic);
                continue;
            }
            int subQos = entry.getValue() == null ? 0 : entry.getValue();
            int eff = normalizeEffectiveQos(pubQos, subQos);
            Integer level = subscriberCtx.channel().attr(PROTOCOL_LEVEL).get();
            boolean mqtt5 = level != null && level == 0x05;
            if (eff == 0) {
                int rl = 2 + topicBytes.length + (mqtt5 ? 1 : 0) + payloadLen;
                ByteBuf out = subscriberCtx.alloc().buffer(5 + rl);
                int fh = 0x30;
                out.writeByte(fh);
                writeRemainingLength(out, rl);
                out.writeShort(topicBytes.length);
                out.writeBytes(topicBytes);
                if (mqtt5) {
                    out.writeByte(0x00);
                }
                out.writeBytes(payload, payloadReaderIndex, payloadLen);
                subscriberCtx.writeAndFlush(out);
            } else if (eff == 2 && qos2Publisher != null) {
                if (payloadBytes == null) {
                    payloadBytes = new byte[payloadLen];
                    payload.getBytes(payloadReaderIndex, payloadBytes);
                }
                int outPacketId = packetIdSupplier.next(subscriberCtx);
                qos2Publisher.publishQos2(subscriberCtx, topic, payloadBytes, false, outPacketId);
                log.debug("下行 PUBLISH QoS2 topic={} outPacketId={} -> subscriberChannelId={}",
                        topic, outPacketId, subscriberId.asShortText());
            } else {
                if (payloadBytes == null) {
                    payloadBytes = new byte[payloadLen];
                    payload.getBytes(payloadReaderIndex, payloadBytes);
                }
                int outPacketId = packetIdSupplier.next(subscriberCtx);
                outboundTracker.track(subscriberCtx, outPacketId, topic, payloadBytes, false);
                int rl = 2 + topicBytes.length + 2 + (mqtt5 ? 1 : 0) + payloadLen;
                ByteBuf out = subscriberCtx.alloc().buffer(5 + rl);
                int fh = 0x32 | (dup ? 0x08 : 0x00);
                out.writeByte(fh);
                writeRemainingLength(out, rl);
                out.writeShort(topicBytes.length);
                out.writeBytes(topicBytes);
                out.writeShort(outPacketId);
                if (mqtt5) {
                    // properties length
                    out.writeByte(0x00);
                }
                out.writeBytes(payload, payloadReaderIndex, payloadLen);
                subscriberCtx.writeAndFlush(out);
                log.debug("下行 PUBLISH QoS1 topic={} outPacketId={} -> subscriberChannelId={}",
                        topic, outPacketId, subscriberId.asShortText());
            }
            n++;
        }
        if (payloadBytes == null) {
            payloadBytes = new byte[payloadLen];
            payload.getBytes(payloadReaderIndex, payloadBytes);
        }
        sessionService.enqueueForOfflineMatches(topic, payloadBytes, false, pubQos);
        return n;
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

    /**
     * Broker 下行 QoS 取 min(pubQos, subQos)，支持 QoS0/1/2。
     */
    private static int normalizeEffectiveQos(int pubQos, int subQos) {
        int eff = Math.min(pubQos, subQos);
        return Math.max(eff, 0);
    }
}
