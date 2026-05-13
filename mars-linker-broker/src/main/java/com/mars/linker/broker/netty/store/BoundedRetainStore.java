package com.mars.linker.broker.netty.store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RetainStore 包装器：增加 TTL 与总量上限约束。
 */
public final class BoundedRetainStore implements RetainStore {
    private final RetainStore delegate;
    private final int maxMessages;
    private final long ttlMs;
    private final Map<String, Long> createdAtByTopic = new ConcurrentHashMap<>();

    public BoundedRetainStore(RetainStore delegate, int maxMessages, long ttlMs) {
        this.delegate = delegate;
        this.maxMessages = maxMessages;
        this.ttlMs = ttlMs;
        long now = System.currentTimeMillis();
        for (RetainedMessage msg : delegate.list()) {
            createdAtByTopic.put(msg.topic, now);
        }
        cleanup(now);
    }

    @Override
    public synchronized void put(String topic, byte[] payload, int qos) {
        if (topic == null || payload == null) {
            return;
        }
        long now = System.currentTimeMillis();
        cleanup(now);
        delegate.put(topic, payload, qos);
        createdAtByTopic.put(topic, now);
        cleanup(now);
    }

    @Override
    public synchronized void remove(String topic) {
        if (topic == null) {
            return;
        }
        delegate.remove(topic);
        createdAtByTopic.remove(topic);
    }

    @Override
    public synchronized List<RetainedMessage> list() {
        cleanup(System.currentTimeMillis());
        return delegate.list();
    }

    @Override
    public synchronized void close() {
        delegate.close();
    }

    private void cleanup(long now) {
        if (ttlMs > 0) {
            List<String> expired = new ArrayList<>();
            for (Map.Entry<String, Long> e : createdAtByTopic.entrySet()) {
                if (now - e.getValue() > ttlMs) {
                    expired.add(e.getKey());
                }
            }
            for (String topic : expired) {
                delegate.remove(topic);
                createdAtByTopic.remove(topic);
            }
        }
        if (maxMessages > 0 && createdAtByTopic.size() > maxMessages) {
            List<Map.Entry<String, Long>> all = new ArrayList<>(createdAtByTopic.entrySet());
            all.sort(Comparator.comparingLong(Map.Entry::getValue));
            int overflow = createdAtByTopic.size() - maxMessages;
            for (int i = 0; i < overflow; i++) {
                String topic = all.get(i).getKey();
                delegate.remove(topic);
                createdAtByTopic.remove(topic);
            }
        }
    }
}
