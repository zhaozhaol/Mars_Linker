package com.mars.linker.broker.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final int MAX_ENTRIES = 500;

    private final CopyOnWriteArrayList<AuditEntry> entries = new CopyOnWriteArrayList<>();

    public void record(String action, String target, String operator, Map<String, Object> changes) {
        AuditEntry entry = new AuditEntry(
                System.currentTimeMillis(),
                action,
                target,
                operator,
                changes != null ? new LinkedHashMap<>(changes) : Map.of()
        );
        entries.add(0, entry);
        trim();
        log.info("Audit: {} {} by {} - {}", action, target, operator, changes);
    }

    public List<AuditEntry> list(int page, int size) {
        int actualPage = Math.max(1, page);
        int actualSize = Math.min(200, Math.max(1, size));
        int fromIndex = (actualPage - 1) * actualSize;
        int toIndex = Math.min(fromIndex + actualSize, entries.size());
        if (fromIndex >= entries.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(entries.subList(fromIndex, toIndex));
    }

    public int size() {
        return entries.size();
    }

    private void trim() {
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    public static class AuditEntry {
        private final long timestamp;
        private final String action;
        private final String target;
        private final String operator;
        private final Map<String, Object> changes;

        public AuditEntry(long timestamp, String action, String target, String operator, Map<String, Object> changes) {
            this.timestamp = timestamp;
            this.action = action;
            this.target = target;
            this.operator = operator;
            this.changes = changes;
        }

        public long getTimestamp() { return timestamp; }
        public String getAction() { return action; }
        public String getTarget() { return target; }
        public String getOperator() { return operator; }
        public Map<String, Object> getChanges() { return changes; }
    }
}
