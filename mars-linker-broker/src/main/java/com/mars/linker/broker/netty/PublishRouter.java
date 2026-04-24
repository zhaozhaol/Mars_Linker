package com.mars.linker.broker.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 发布路由与下行投递（QoS0/1）处理。
 */
final class PublishRouter {
    private static final AttributeKey<Integer> PROTOCOL_LEVEL = AttributeKey.valueOf("mqtt_protocol_level");

    interface PacketIdSupplier {
        int next(ChannelHandlerContext ctx);
    }

    interface OutboundTracker {
        void track(ChannelHandlerContext ctx, int packetId, String topic, byte[] payload, boolean retain);
    }

    private PublishRouter() {
    }

    static int publishAndEnqueueOffline(String topic,
                                        byte[] payload,
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
                                        Logger log) {
        Map<ChannelId, Integer> grantedQosBySubscriber = subscriptionRegistry.collectGrantedQos(
                topic,
                grantedQosLookup,
                channelId -> {
                    ChannelHandlerContext c = channels.get(channelId);
                    return c != null && c.channel().isActive();
                }
        );

        if (grantedQosBySubscriber.isEmpty()) {
            enqueueForOfflinePersistentSessions(topic, payload, retain, pubQos, sessionService, clientToChannel, channels);
            log.trace("PUBLISH 无订阅者 topic={}", topic);
            return 0;
        }

        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
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
                int rl = 2 + topicBytes.length + (mqtt5 ? 1 : 0) + payload.length;
                ByteBuf out = Unpooled.buffer();
                int fh = 0x30 | (retain ? 0x01 : 0x00);
                out.writeByte(fh);
                writeRemainingLength(out, rl);
                out.writeShort(topicBytes.length);
                out.writeBytes(topicBytes);
                if (mqtt5) {
                    out.writeByte(0x00); // properties length
                }
                out.writeBytes(payload);
                subscriberCtx.writeAndFlush(out);
            } else {
                int outPacketId = packetIdSupplier.next(subscriberCtx);
                outboundTracker.track(subscriberCtx, outPacketId, topic, payload, retain);
                int rl = 2 + topicBytes.length + 2 + (mqtt5 ? 1 : 0) + payload.length;
                ByteBuf out = Unpooled.buffer();
                int fh = 0x32 | (dup ? 0x08 : 0x00) | (retain ? 0x01 : 0x00);
                out.writeByte(fh);
                writeRemainingLength(out, rl);
                out.writeShort(topicBytes.length);
                out.writeBytes(topicBytes);
                out.writeShort(outPacketId);
                if (mqtt5) {
                    out.writeByte(0x00); // properties length
                }
                out.writeBytes(payload);
                subscriberCtx.writeAndFlush(out);
                log.debug("下行 PUBLISH QoS1 topic={} outPacketId={} -> subscriberChannelId={}",
                        topic, outPacketId, subscriberId.asShortText());
            }
            n++;
        }
        enqueueForOfflinePersistentSessions(topic, payload, retain, pubQos, sessionService, clientToChannel, channels);
        return n;
    }

    private static void enqueueForOfflinePersistentSessions(String topic,
                                                            byte[] payload,
                                                            boolean retain,
                                                            int pubQos,
                                                            SessionService sessionService,
                                                            Map<String, ChannelId> clientToChannel,
                                                            Map<ChannelId, ChannelHandlerContext> channels) {
        boolean changed = false;
        for (SessionService.Session session : sessionService.allSessions()) {
            if (session == null || session.subscriptionsQos.isEmpty()) {
                continue;
            }
            ChannelId activeChannelId = clientToChannel.get(session.clientId);
            ChannelHandlerContext activeCtx = activeChannelId == null ? null : channels.get(activeChannelId);
            if (activeCtx != null && activeCtx.channel().isActive()) {
                continue;
            }
            int granted = -1;
            for (Map.Entry<String, Integer> sub : session.subscriptionsQos.entrySet()) {
                if (TopicFilterSupport.matchTopicFilter(sub.getKey(), topic)) {
                    int q = sub.getValue() == null ? 0 : sub.getValue();
                    granted = Math.max(granted, q);
                }
            }
            if (granted < 0) {
                continue;
            }
            int eff = normalizeEffectiveQos(pubQos, granted);
            byte[] copy = new byte[payload.length];
            System.arraycopy(payload, 0, copy, 0, payload.length);
            session.offlineQueue.add(new SessionService.QueuedMessage(topic, copy, retain, eff));
            changed = true;
        }
        if (changed) {
            sessionService.persist();
        }
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
     * Broker 下行仅支持 QoS0/1：即使发布端与订阅端都声明 QoS2，也会降级为 QoS1。
     */
    private static int normalizeEffectiveQos(int pubQos, int subQos) {
        int eff = Math.min(pubQos, subQos);
        if (eff <= 0) {
            return 0;
        }
        return Math.min(eff, 1);
    }
}
