package com.mars.linker.broker.netty.protocol;

import io.netty.channel.ChannelId;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订阅注册表（精确/通配符/$share）与共享订阅轮询选择。
 */
public final class SubscriptionRegistry {
    public interface GrantedQosLookup {
        public int lookup(ChannelId channelId, String topicOrFilter);
    }

    public interface ChannelActiveProbe {
        public boolean isActive(ChannelId channelId);
    }

    private final Map<String, CopyOnWriteArraySet<ChannelId>> exactTopicSubscribers = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArraySet<ChannelId>> wildcardSubscribers = new ConcurrentHashMap<>();
    private final TopicFilterSupport.TopicFilterIndex wildcardFilterIndex = new TopicFilterSupport.TopicFilterIndex();
    private final Map<String, Map<String, CopyOnWriteArraySet<ChannelId>>> shareSubscribers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> shareRoundRobin = new ConcurrentHashMap<>();

    public void add(ChannelId channelId, String topicFilter) {
        if (TopicFilterSupport.isShareSubscription(topicFilter)) {
            TopicFilterSupport.ShareSubscription ss = TopicFilterSupport.parseShareSubscription(topicFilter);
            if (ss == null) {
                return;
            }
            shareSubscribers
                    .computeIfAbsent(ss.group, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(ss.filter, k -> new CopyOnWriteArraySet<>())
                    .add(channelId);
            return;
        }
        if (TopicFilterSupport.isExactTopic(topicFilter)) {
            exactTopicSubscribers.computeIfAbsent(topicFilter, k -> new CopyOnWriteArraySet<>()).add(channelId);
            return;
        }
        wildcardSubscribers.computeIfAbsent(topicFilter, k -> new CopyOnWriteArraySet<>()).add(channelId);
        wildcardFilterIndex.add(topicFilter);
    }

    public boolean remove(ChannelId channelId, String topicFilter) {
        boolean removed = false;
        if (TopicFilterSupport.isShareSubscription(topicFilter)) {
            TopicFilterSupport.ShareSubscription ss = TopicFilterSupport.parseShareSubscription(topicFilter);
            if (ss == null) {
                return false;
            }
            final boolean[] r = {false};
            shareSubscribers.computeIfPresent(ss.group, (group, filterMap) -> {
                filterMap.computeIfPresent(ss.filter, (filter, subscribers) -> {
                    r[0] = subscribers.remove(channelId);
                    return subscribers.isEmpty() ? null : subscribers;
                });
                return filterMap.isEmpty() ? null : filterMap;
            });
            return r[0];
        }
        Map<String, CopyOnWriteArraySet<ChannelId>> target = TopicFilterSupport.isExactTopic(topicFilter)
                ? exactTopicSubscribers
                : wildcardSubscribers;
        final boolean[] r = {false};
        target.computeIfPresent(topicFilter, (filter, subscribers) -> {
            r[0] = subscribers.remove(channelId);
            if (subscribers.isEmpty()) {
                if (!TopicFilterSupport.isExactTopic(topicFilter)) {
                    wildcardFilterIndex.remove(topicFilter);
                }
                return null;
            }
            return subscribers;
        });
        return r[0];
    }

    public Map<ChannelId, Integer> collectGrantedQos(String topic,
                                              GrantedQosLookup qosLookup,
                                              ChannelActiveProbe activeProbe) {
        Map<ChannelId, Integer> grantedQosBySubscriber = new HashMap<>();

        Set<ChannelId> exactSubs = exactTopicSubscribers.get(topic);
        if (exactSubs != null) {
            for (ChannelId sid : exactSubs) {
                int granted = qosLookup.lookup(sid, topic);
                grantedQosBySubscriber.merge(sid, granted, Math::max);
            }
        }

        if (!wildcardSubscribers.isEmpty()) {
            Set<String> matchedFilters = wildcardFilterIndex.match(topic);
            for (String filter : matchedFilters) {
                Set<ChannelId> subscribers = wildcardSubscribers.get(filter);
                if (subscribers == null || subscribers.isEmpty()) {
                    continue;
                }
                for (ChannelId sid : subscribers) {
                    int granted = qosLookup.lookup(sid, filter);
                    grantedQosBySubscriber.merge(sid, granted, Math::max);
                }
            }
        }

        if (!shareSubscribers.isEmpty()) {
            for (Map.Entry<String, Map<String, CopyOnWriteArraySet<ChannelId>>> ge : shareSubscribers.entrySet()) {
                String group = ge.getKey();
                Map<String, CopyOnWriteArraySet<ChannelId>> filters = ge.getValue();
                if (filters == null || filters.isEmpty()) {
                    continue;
                }
                for (Map.Entry<String, CopyOnWriteArraySet<ChannelId>> fe : filters.entrySet()) {
                    String filter = fe.getKey();
                    if (!TopicFilterSupport.matchTopicFilter(filter, topic)) {
                        continue;
                    }
                    ChannelId selected = selectShareSubscriber(group, filter, fe.getValue(), activeProbe);
                    if (selected == null) {
                        continue;
                    }
                    String fullFilter = "$share/" + group + "/" + filter;
                    int granted = qosLookup.lookup(selected, fullFilter);
                    grantedQosBySubscriber.merge(selected, granted, Math::max);
                }
            }
        }

        return grantedQosBySubscriber;
    }

    public void clear() {
        exactTopicSubscribers.clear();
        wildcardSubscribers.clear();
        wildcardFilterIndex.clear();
        shareSubscribers.clear();
        shareRoundRobin.clear();
    }

    public int subscriptionTotal() {
        int total = 0;
        for (Set<ChannelId> subs : exactTopicSubscribers.values()) {
            total += subs.size();
        }
        for (Set<ChannelId> subs : wildcardSubscribers.values()) {
            total += subs.size();
        }
        for (Map<String, CopyOnWriteArraySet<ChannelId>> group : shareSubscribers.values()) {
            for (Set<ChannelId> subs : group.values()) {
                total += subs.size();
            }
        }
        return total;
    }

    public int topicCount() {
        return exactTopicSubscribers.size() + wildcardSubscribers.size();
    }

    public int treeDepth() {
        return wildcardSubscribers.size();
    }

    public Map<String, CopyOnWriteArraySet<ChannelId>> exactTopicSubscribers() {
        return exactTopicSubscribers;
    }

    public Map<String, CopyOnWriteArraySet<ChannelId>> wildcardSubscribers() {
        return wildcardSubscribers;
    }

    public Map<String, Map<String, CopyOnWriteArraySet<ChannelId>>> shareSubscribers() {
        return shareSubscribers;
    }

    private ChannelId selectShareSubscriber(String group,
                                            String filter,
                                            CopyOnWriteArraySet<ChannelId> subscribers,
                                            ChannelActiveProbe activeProbe) {
        if (subscribers == null || subscribers.isEmpty()) {
            return null;
        }
        ChannelId[] arr = subscribers.toArray(new ChannelId[0]);
        if (arr.length == 0) {
            return null;
        }
        String key = group + "|" + filter;
        AtomicInteger rr = shareRoundRobin.computeIfAbsent(key, k -> new AtomicInteger(0));
        int start = Math.floorMod(rr.getAndIncrement() & 0x7FFFFFFF, arr.length);
        for (int i = 0; i < arr.length; i++) {
            ChannelId candidate = arr[(start + i) % arr.length];
            if (activeProbe.isActive(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
