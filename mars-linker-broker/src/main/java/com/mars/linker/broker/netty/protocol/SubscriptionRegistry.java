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

    private final Map<String, CopyOnWriteArraySet<ChannelId>> exactTopicSubscribers = new ConcurrentHashMap<>(4096);
    private final Map<String, CopyOnWriteArraySet<ChannelId>> wildcardSubscribers = new ConcurrentHashMap<>(256);
    private final TopicFilterSupport.TopicFilterIndex wildcardFilterIndex = new TopicFilterSupport.TopicFilterIndex();
    private final Map<String, Map<String, CopyOnWriteArraySet<ChannelId>>> shareSubscribers = new ConcurrentHashMap<>(64);
    private final Map<String, AtomicInteger> shareRoundRobin = new ConcurrentHashMap<>(64);
    private final AtomicInteger cachedSubscriptionTotal = new AtomicInteger(0);

    public void add(ChannelId channelId, String topicFilter) {
        if (TopicFilterSupport.isShareSubscription(topicFilter)) {
            TopicFilterSupport.ShareSubscription ss = TopicFilterSupport.parseShareSubscription(topicFilter);
            if (ss == null) {
                return;
            }
            boolean added = shareSubscribers
                    .computeIfAbsent(ss.group, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(ss.filter, k -> new CopyOnWriteArraySet<>())
                    .add(channelId);
            if (added) {
                cachedSubscriptionTotal.incrementAndGet();
            }
            return;
        }
        if (TopicFilterSupport.isExactTopic(topicFilter)) {
            boolean added = exactTopicSubscribers
                    .computeIfAbsent(topicFilter, k -> new CopyOnWriteArraySet<>())
                    .add(channelId);
            if (added) {
                cachedSubscriptionTotal.incrementAndGet();
            }
            return;
        }
        boolean added = wildcardSubscribers
                .computeIfAbsent(topicFilter, k -> new CopyOnWriteArraySet<>())
                .add(channelId);
        if (added) {
            wildcardFilterIndex.add(topicFilter);
            cachedSubscriptionTotal.incrementAndGet();
        }
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
                    return subscribers.isEmpty() ? null : subscribers;                });
                return filterMap.isEmpty() ? null : filterMap;
            });
            if (r[0]) cachedSubscriptionTotal.decrementAndGet();
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
        if (r[0]) cachedSubscriptionTotal.decrementAndGet();
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
        cachedSubscriptionTotal.set(0);
    }

    public int subscriptionTotal() {
        return cachedSubscriptionTotal.get();
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
