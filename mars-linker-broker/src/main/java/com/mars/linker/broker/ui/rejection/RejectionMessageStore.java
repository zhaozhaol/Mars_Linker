package com.mars.linker.broker.ui.rejection;

import com.mars.linker.broker.ui.monitoring.model.PagedResult;
import com.mars.linker.broker.ui.rejection.model.RejectionMessage;
import com.mars.linker.broker.ui.rejection.model.RejectionReason;
import com.mars.linker.broker.ui.rejection.model.RejectionSummary;
import com.mars.linker.broker.ui.rejection.model.RejectionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class RejectionMessageStore {

    private final ConcurrentLinkedDeque<RejectionMessage> deque = new ConcurrentLinkedDeque<>();
    private final AtomicLong idGenerator = new AtomicLong(0);
    private final AtomicLong currentSize = new AtomicLong(0);
    private final int maxCapacity;

    public RejectionMessageStore(@Value("${mars.linker.rejection.max-capacity:1000}") int maxCapacity) {
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : 1000;
    }

    public void add(RejectionMessage message) {
        message.setId(idGenerator.incrementAndGet());
        message.setTimestamp(System.currentTimeMillis());
        deque.addLast(message);
        long size = currentSize.incrementAndGet();
        while (size > maxCapacity) {
            RejectionMessage oldest = deque.pollFirst();
            if (oldest != null) {
                size = currentSize.decrementAndGet();
            } else {
                break;
            }
        }
    }

    public List<RejectionMessage> listAll() {
        return new ArrayList<>(deque);
    }

    public PagedResult<RejectionMessage> listPaged(int page, int size, RejectionType type, RejectionReason reason) {
        List<RejectionMessage> filtered = new ArrayList<>();
        for (RejectionMessage msg : deque) {
            if (type != null && msg.getType() != type) continue;
            if (reason != null && msg.getReason() != reason) continue;
            filtered.add(msg);
        }
        filtered.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        long totalCount = filtered.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<RejectionMessage> pageItems;
        if (fromIndex >= filtered.size()) {
            pageItems = List.of();
        } else {
            pageItems = filtered.subList(fromIndex, toIndex);
        }
        return new PagedResult<>(pageItems, totalCount, page, size);
    }

    public boolean removeById(long id) {
        for (RejectionMessage msg : deque) {
            if (msg.getId() == id) {
                if (deque.remove(msg)) {
                    currentSize.decrementAndGet();
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public long removeByType(RejectionType type) {
        List<RejectionMessage> toRemove = deque.stream()
                .filter(msg -> msg.getType() == type)
                .collect(Collectors.toList());
        long removed = 0;
        for (RejectionMessage msg : toRemove) {
            if (deque.remove(msg)) {
                currentSize.decrementAndGet();
                removed++;
            }
        }
        return removed;
    }

    public long removeAll() {
        long count = currentSize.get();
        deque.clear();
        currentSize.set(0);
        return count;
    }

    public RejectionSummary getSummary() {
        long connectRefused = 0;
        long aclSubscribeDenied = 0;
        long aclPublishDenied = 0;
        for (RejectionMessage msg : deque) {
            switch (msg.getType()) {
                case CONNECT_REFUSED: connectRefused++; break;
                case ACL_SUBSCRIBE_DENIED: aclSubscribeDenied++; break;
                case ACL_PUBLISH_DENIED: aclPublishDenied++; break;
            }
        }
        long total = connectRefused + aclSubscribeDenied + aclPublishDenied;
        return new RejectionSummary(connectRefused, aclSubscribeDenied, aclPublishDenied, total);
    }
}
