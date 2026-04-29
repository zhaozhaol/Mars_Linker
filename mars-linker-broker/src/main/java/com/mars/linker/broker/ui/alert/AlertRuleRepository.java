package com.mars.linker.broker.ui.alert;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AlertRuleRepository {

    private final Map<String, AlertRule> rules = new ConcurrentHashMap<>();
    private final List<AlertEvent> events = new CopyOnWriteArrayList<>();
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
        String id = "rule-" + ruleIdSeq.getAndIncrement();
        rule.setId(id);
        long now = System.currentTimeMillis();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rules.put(id, rule);
        return rule;
    }

    public Optional<AlertRule> updateRule(String id, AlertRule update) {
        return Optional.ofNullable(rules.computeIfPresent(id, (k, existing) -> {
            existing.setName(update.getName());
            existing.setMetric(update.getMetric());
            existing.setOperator(update.getOperator());
            existing.setThreshold(update.getThreshold());
            existing.setDurationSeconds(update.getDurationSeconds());
            existing.setEnabled(update.isEnabled());
            existing.setUpdatedAt(System.currentTimeMillis());
            return existing;
        }));
    }

    public boolean deleteRule(String id) {
        return rules.remove(id) != null;
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

    public AlertEvent recordTrigger(AlertRule rule, double actualValue) {
        AlertEvent event = new AlertEvent();
        event.setId("evt-" + eventIdSeq.getAndIncrement());
        event.setRuleId(rule.getId());
        event.setRuleName(rule.getName());
        event.setMetric(rule.getMetric());
        event.setActualValue(actualValue);
        event.setThreshold(rule.getThreshold());
        event.setSeverity("critical");
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
            }
        }
    }

    private void trimEvents() {
        while (events.size() > MAX_EVENTS) {
            events.remove(events.size() - 1);
        }
    }
}
