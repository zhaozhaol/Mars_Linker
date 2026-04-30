package com.mars.linker.broker.ui.monitoring;

import com.mars.linker.broker.netty.ClientSessionContext;
import com.mars.linker.broker.netty.MqttProtocolHandler;
import com.mars.linker.broker.netty.protocol.SubscriptionRegistry;
import com.mars.linker.broker.ui.monitoring.isolation.MonitoringFaultBoundary;
import com.mars.linker.broker.ui.monitoring.model.ClientSubscriptionInfo;
import com.mars.linker.broker.ui.monitoring.model.PagedResult;
import com.mars.linker.broker.ui.monitoring.model.SubscriberDetail;
import com.mars.linker.broker.ui.monitoring.model.SubscriptionTopicInfo;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.util.AttributeKey;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class SubscriptionDetailService {

    private static final int PAGINATION_THRESHOLD = 10000;

    public PagedResult<SubscriptionTopicInfo> listTopics(int page, int size) {
        return MonitoringFaultBoundary.executeWithResult(() -> {
            SubscriptionRegistry registry = MqttProtocolHandler.subscriptionRegistry();
            List<SubscriptionTopicInfo> all = new ArrayList<>();

            all.addAll(collectFromMap(registry.exactTopicSubscribers(), "exact"));
            all.addAll(collectFromMap(registry.wildcardSubscribers(), "wildcard"));
            all.addAll(collectFromShareMap(registry.shareSubscribers()));

            long total = all.size();
            int fromIndex = (page - 1) * size;
            int toIndex = Math.min(fromIndex + size, all.size());
            List<SubscriptionTopicInfo> paged = fromIndex < all.size()
                    ? all.subList(fromIndex, toIndex)
                    : Collections.emptyList();
            return new PagedResult<>(paged, total, page, size);
        }, new PagedResult<>(Collections.emptyList(), 0, page, size));
    }

    public List<SubscriberDetail> listSubscribers(String topicFilter) {
        return MonitoringFaultBoundary.executeWithResult(() -> {
            SubscriptionRegistry registry = MqttProtocolHandler.subscriptionRegistry();
            Set<ChannelId> channelIds = findChannelIds(registry, topicFilter);
            if (channelIds == null || channelIds.isEmpty()) {
                return Collections.emptyList();
            }

            Map<ChannelId, ChannelHandlerContext> channels = MqttProtocolHandler.channels();
            AttributeKey<ConcurrentHashMap<String, Integer>> qosKey =
                    AttributeKey.valueOf("mqtt_subscription_qos");

            List<SubscriberDetail> result = new ArrayList<>();
            for (ChannelId cid : channelIds) {
                ChannelHandlerContext ctx = channels.get(cid);
                if (ctx == null) continue;
                result.add(buildSubscriberDetail(ctx, topicFilter, qosKey));
            }
            return result;
        }, Collections.emptyList());
    }

    public ClientSubscriptionInfo listClientSubscriptions(String clientId) {
        return MonitoringFaultBoundary.executeWithResult(() -> {
            Map<String, ChannelId> clientToChannel = MqttProtocolHandler.clientToChannel();
            ChannelId channelId = clientToChannel.get(clientId);
            if (channelId == null) {
                return new ClientSubscriptionInfo(clientId, Collections.emptyList());
            }

            Map<ChannelId, ChannelHandlerContext> channels = MqttProtocolHandler.channels();
            ChannelHandlerContext ctx = channels.get(channelId);
            if (ctx == null) {
                return new ClientSubscriptionInfo(clientId, Collections.emptyList());
            }

            Channel ch = ctx.channel();
            @SuppressWarnings("unchecked")
            Set<String> topicSubs = (Set<String>) ch.attr(
                    AttributeKey.<Set<String>>valueOf("mqtt_topic_subscriptions")).get();

            List<SubscriptionTopicInfo> subs = new ArrayList<>();
            if (topicSubs != null) {
                for (String tf : topicSubs) {
                    subs.add(new SubscriptionTopicInfo(tf, 1, inferType(tf)));
                }
            }
            return new ClientSubscriptionInfo(clientId, subs);
        }, new ClientSubscriptionInfo(clientId, Collections.emptyList()));
    }

    private List<SubscriptionTopicInfo> collectFromMap(
            Map<String, ? extends Set<ChannelId>> map, String type) {
        List<SubscriptionTopicInfo> list = new ArrayList<>();
        for (Map.Entry<String, ? extends Set<ChannelId>> e : map.entrySet()) {
            list.add(new SubscriptionTopicInfo(e.getKey(), e.getValue().size(), type));
        }
        return list;
    }

    private List<SubscriptionTopicInfo> collectFromShareMap(
            Map<String, Map<String, CopyOnWriteArraySet<ChannelId>>> shareMap) {
        List<SubscriptionTopicInfo> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, CopyOnWriteArraySet<ChannelId>>> ge : shareMap.entrySet()) {
            for (Map.Entry<String, CopyOnWriteArraySet<ChannelId>> fe : ge.getValue().entrySet()) {
                String fullFilter = "$share/" + ge.getKey() + "/" + fe.getKey();
                list.add(new SubscriptionTopicInfo(fullFilter, fe.getValue().size(), "shared"));
            }
        }
        return list;
    }

    private Set<ChannelId> findChannelIds(SubscriptionRegistry registry, String topicFilter) {
        if (topicFilter.startsWith("$share/")) {
            int firstSlash = topicFilter.indexOf('/', 7);
            if (firstSlash < 0) return null;
            String group = topicFilter.substring(7, firstSlash);
            String filter = topicFilter.substring(firstSlash + 1);
            Map<String, CopyOnWriteArraySet<ChannelId>> groupMap = registry.shareSubscribers().get(group);
            if (groupMap == null) return null;
            return groupMap.get(filter);
        }
        if (!topicFilter.contains("+") && !topicFilter.contains("#")) {
            return registry.exactTopicSubscribers().get(topicFilter);
        }
        return registry.wildcardSubscribers().get(topicFilter);
    }

    private SubscriberDetail buildSubscriberDetail(ChannelHandlerContext ctx,
                                                    String topicFilter,
                                                    AttributeKey<ConcurrentHashMap<String, Integer>> qosKey) {
        ClientSessionContext session = ClientSessionContext.of(ctx);
        String cId = session.clientId() != null ? session.clientId() : "";
        Integer protocolLevel = session.protocolLevel();
        int pLevel = protocolLevel != null ? protocolLevel : 0;

        ConcurrentHashMap<String, Integer> qosMap = ctx.channel().attr(qosKey).get();
        int grantedQos = 0;
        if (qosMap != null) {
            Integer q = qosMap.get(topicFilter);
            if (q != null) grantedQos = q;
        }

        Long lastPacket = session.lastPacketAtMs();
        long now = System.currentTimeMillis();
        long duration = lastPacket != null ? now - lastPacket : 0;

        String remote = "";
        try {
            remote = ctx.channel().remoteAddress().toString();
        } catch (Exception ignored) {}

        return new SubscriberDetail(cId, grantedQos, pLevel, duration, remote);
    }

    private String inferType(String topicFilter) {
        if (topicFilter.startsWith("$share/")) return "shared";
        if (topicFilter.contains("+") || topicFilter.contains("#")) return "wildcard";
        return "exact";
    }
}
