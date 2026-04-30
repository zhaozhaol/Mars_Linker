package com.mars.linker.broker.ui.monitoring.history;

import com.mars.linker.broker.ui.monitoring.isolation.MonitoringFaultBoundary;
import com.mars.linker.broker.ui.monitoring.isolation.MonitoringResourceBudget;
import com.mars.linker.broker.ui.monitoring.model.MonitoringSnapshot;
import com.mars.linker.broker.ui.monitoring.model.PagedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringHistoryService.class);
    private static final int DEFAULT_CAPACITY = 7200;

    private final ConcurrentLinkedDeque<MonitoringSnapshot> buffer = new ConcurrentLinkedDeque<>();
    private final MonitoringResourceBudget resourceBudget;
    private final int capacity;

    public MonitoringHistoryService(MonitoringResourceBudget resourceBudget) {
        this.resourceBudget = resourceBudget;
        this.capacity = DEFAULT_CAPACITY;
    }

    public void record(MonitoringSnapshot snapshot) {
        MonitoringFaultBoundary.execute(() -> {
            if (!resourceBudget.checkHistoryMemory()) {
                MonitoringSnapshot oldest = buffer.pollLast();
                if (oldest != null) {
                    resourceBudget.reduceHistoryMemory(estimateSize(oldest));
                }
            }
            buffer.addFirst(snapshot);
            resourceBudget.addHistoryMemory(estimateSize(snapshot));
            while (buffer.size() > capacity) {
                MonitoringSnapshot removed = buffer.pollLast();
                if (removed != null) {
                    resourceBudget.reduceHistoryMemory(estimateSize(removed));
                }
            }
        });
    }

    public PagedResult<MonitoringSnapshot> query(Long start, Long end, String category, int page, int size) {
        int actualPage = Math.max(1, page);
        int actualSize = Math.min(1000, Math.max(1, size));
        long now = System.currentTimeMillis();
        long actualStart = start != null ? start : now - 3600_000L;
        long actualEnd = end != null ? end : now;
        if (actualStart > actualEnd) {
            throw new IllegalArgumentException("start must not be later than end");
        }

        List<MonitoringSnapshot> filtered = new ArrayList<>();
        for (MonitoringSnapshot snapshot : buffer) {
            if (snapshot.getTimestamp() < actualStart || snapshot.getTimestamp() > actualEnd) continue;
            if (category != null && !category.isEmpty() && !category.equals(snapshot.getCategory())) continue;
            filtered.add(snapshot);
        }

        long totalCount = filtered.size();
        int fromIndex = (actualPage - 1) * actualSize;
        int toIndex = Math.min(fromIndex + actualSize, filtered.size());
        List<MonitoringSnapshot> pageItems;
        if (fromIndex >= filtered.size()) {
            pageItems = new ArrayList<>();
        } else {
            pageItems = filtered.subList(fromIndex, toIndex);
        }
        return new PagedResult<>(pageItems, totalCount, actualPage, actualSize);
    }

    public int bufferSize() {
        return buffer.size();
    }

    private long estimateSize(MonitoringSnapshot snapshot) {
        return 256;
    }
}
