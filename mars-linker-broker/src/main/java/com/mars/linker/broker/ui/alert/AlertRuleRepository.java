package com.mars.linker.broker.ui.alert;

import com.mars.linker.broker.ui.monitoring.model.PagedResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AlertRuleRepository {

    private final Map<String, AlertRule> rules = new ConcurrentHashMap<>();
    private final List<AlertEvent> events = new CopyOnWriteArrayList<>();
    private final Map<String, Long> silencedUntil = new ConcurrentHashMap<>();
    private final AtomicLong ruleIdSeq = new AtomicLong(1);
    private final AtomicLong eventIdSeq = new AtomicLong(1);
    private static final int MAX_EVENTS = 500;

    public List<AlertRule> listRules() {
        return new ArrayList<>(rules.values());
    }

    public Optional<AlertRule> getRule(String id) {
        return Optional.ofNullable(rules.get(id));
    }

    public AlertRule createRule(AlertRule rule) {
        validateRule(rule);
        String id = "rule-" + ruleIdSeq.getAndIncrement();
        rule.setId(id);
        long now = System.currentTimeMillis();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rules.put(id, rule);
        return rule;
    }

    public Optional<AlertRule> updateRule(String id, AlertRule update) {
        validateRule(update);
        return Optional.ofNullable(rules.computeIfPresent(id, (k, existing) -> {
            boolean thresholdChanged = existing.getThreshold() != update.getThreshold()
                    || !existing.getOperator().equals(update.getOperator());
            existing.setName(update.getName());
            existing.setMetric(update.getMetric());
            existing.setOperator(update.getOperator());
            existing.setThreshold(update.getThreshold());
            existing.setDurationSeconds(update.getDurationSeconds());
            existing.setEnabled(update.isEnabled());
            existing.setUpdatedAt(System.currentTimeMillis());
            if (thresholdChanged) {
                for (AlertEvent e : events) {
                    if (e.getRuleId().equals(id) && e.isActive()) {
                        e.setActive(false);
                        e.setResolvedAt(System.currentTimeMillis());
                        e.setResolvedBy("threshold_change");
                        e.setStatus("resolved");
                    }
                }
            }
            return existing;
        }));
    }

    public boolean deleteRule(String id) {
        silencedUntil.remove(id);
        return rules.remove(id) != null;
    }

    public void silenceRule(String ruleId, long untilMs) {
        if (!rules.containsKey(ruleId)) {
            throw new IllegalArgumentException("Rule not found: " + ruleId);
        }
        silencedUntil.put(ruleId, untilMs);
    }

    public boolean isSilenced(String ruleId) {
        Long until = silencedUntil.get(ruleId);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            silencedUntil.remove(ruleId);
            return false;
        }
        return true;
    }

    public List<AlertEvent> listEvents(boolean activeOnly) {
        if (activeOnly) {
            List<AlertEvent> result = new ArrayList<>();
            for (AlertEvent e : events) {
                if (e.isActive()) result.add(e);
            }
            return result;
        }
        return new ArrayList<>(events);
    }

    public PagedResult<AlertEvent> listHistory(Long start, Long end, int page, int size) {
        int actualPage = Math.max(1, page);
        int actualSize = Math.min(500, Math.max(1, size));

        List<AlertEvent> filtered = new ArrayList<>();
        for (AlertEvent e : events) {
            if (start != null && e.getTriggeredAt() < start) continue;
            if (end != null && e.getTriggeredAt() > end) continue;
            filtered.add(e);
        }

        long totalCount = filtered.size();
        int fromIndex = (actualPage - 1) * actualSize;
        int toIndex = Math.min(fromIndex + actualSize, filtered.size());
        List<AlertEvent> pageItems;
        if (fromIndex >= filtered.size()) {
            pageItems = new ArrayList<>();
        } else {
            pageItems = filtered.subList(fromIndex, toIndex);
        }
        return new PagedResult<>(pageItems, totalCount, actualPage, actualSize);
    }

    public AlertEvent recordTrigger(AlertRule rule, double actualValue) {
        AlertEvent event = new AlertEvent();
        event.setId("evt-" + eventIdSeq.getAndIncrement());
        event.setRuleId(rule.getId());
        event.setRuleName(rule.getName());
        event.setMetric(rule.getMetric());
        event.setActualValue(actualValue);
        event.setThreshold(rule.getThreshold());
        event.setSeverity(rule.getSeverity() != null ? rule.getSeverity() : "critical");
        event.setTriggeredAt(System.currentTimeMillis());
        event.setActive(true);
        events.add(0, event);
        trimEvents();
        return event;
    }

    public void resolveEvent(String ruleId) {
        for (AlertEvent e : events) {
            if (e.getRuleId().equals(ruleId) && e.isActive()) {
                e.setActive(false);
                e.setResolvedAt(System.currentTimeMillis());
                e.setStatus("resolved");
            }
        }
    }

    public AlertEvent acknowledgeEvent(String eventId, String user) {
        for (AlertEvent e : events) {
            if (e.getId().equals(eventId) && e.isActive()) {
                e.acknowledge(user);
                return e;
            }
        }
        return null;
    }

    public AlertEvent manualResolveEvent(String eventId, String user) {
        for (AlertEvent e : events) {
            if (e.getId().equals(eventId) && e.isActive()) {
                e.manualResolve(user);
                return e;
            }
        }
        return null;
    }

    public void validateRule(AlertRule rule) {
        if (rule.getName() == null || rule.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Rule name must not be empty");
        }
        if (rule.getMetric() == null || rule.getMetric().trim().isEmpty()) {
            throw new IllegalArgumentException("Rule metric must not be empty");
        }
        if (rule.getOperator() == null || !Arrays.asList(">", ">=", "<", "<=", "==").contains(rule.getOperator())) {
            throw new IllegalArgumentException("Invalid operator: " + rule.getOperator());
        }
    }

    private void trimEvents() {
        while (events.size() > MAX_EVENTS) {
            events.remove(events.size() - 1);
        }
    }
}
