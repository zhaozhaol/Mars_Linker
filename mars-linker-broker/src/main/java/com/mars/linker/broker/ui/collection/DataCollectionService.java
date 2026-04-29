package com.mars.linker.broker.ui.collection;

import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 负责采集并缓存界面展示用事件。
 */
@Service
public class DataCollectionService {

    private final MarsLinkerUiProperties uiProperties;
    private final Deque<CollectedEvent> events = new ConcurrentLinkedDeque<>();
    private final AtomicLong totalCollected = new AtomicLong();

    public DataCollectionService(MarsLinkerUiProperties uiProperties) {
        this.uiProperties = uiProperties;
    }

    public CollectedEvent collect(String type, String source, String payload) {
        CollectedEvent event = new CollectedEvent(
                Instant.now().toEpochMilli(),
                normalize(type, "custom"),
                normalize(source, "ui"),
                payload == null ? "" : payload
        );
        events.addFirst(event);
        totalCollected.incrementAndGet();
        trimIfNecessary();
        return event;
    }

    public List<CollectedEvent> listRecent(int limit) {
        int actualLimit = Math.max(1, limit);
        List<CollectedEvent> result = new ArrayList<>(Math.min(actualLimit, events.size()));
        int count = 0;
        for (CollectedEvent event : events) {
            result.add(event);
            count++;
            if (count >= actualLimit) {
                break;
            }
        }
        return result;
    }

    public long totalCollected() {
        return totalCollected.get();
    }

    public int bufferedSize() {
        return events.size();
    }

    private void trimIfNecessary() {
        int maxSize = Math.max(1, uiProperties.getCollectionBufferSize());
        while (events.size() > maxSize) {
            events.pollLast();
        }
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
