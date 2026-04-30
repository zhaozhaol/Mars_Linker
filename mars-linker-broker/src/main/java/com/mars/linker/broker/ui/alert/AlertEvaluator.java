package com.mars.linker.broker.ui.alert;

import com.mars.linker.broker.netty.MqttProtocolHandler;
import com.mars.linker.broker.ui.config.RuntimeConfigService;
import com.mars.linker.broker.ui.monitoring.push.PushDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluator.class);

    private final AlertRuleRepository repository;
    private final RuntimeConfigService runtimeConfigService;
    private final PushDispatcher pushDispatcher;
    private final Map<String, Long> violationSince = new ConcurrentHashMap<>();

    public AlertEvaluator(AlertRuleRepository repository,
                          RuntimeConfigService runtimeConfigService,
                          PushDispatcher pushDispatcher) {
        this.repository = repository;
        this.runtimeConfigService = runtimeConfigService;
        this.pushDispatcher = pushDispatcher;
    }

    @Scheduled(fixedDelay = 5000)
    public void evaluate() {
        if (!runtimeConfigService.isAlertEvaluationEnabled()) {
            return;
        }
        Map<String, Double> metrics = collectMetrics();
        List<AlertRule> rules = repository.listRules();
        for (AlertRule rule : rules) {
            if (!rule.isEnabled()) {
                violationSince.remove(rule.getId());
                continue;
            }
            Double value = metrics.get(rule.getMetric());
            if (value == null) continue;
            boolean violated = evaluateCondition(value, rule.getOperator(), rule.getThreshold());
            if (violated) {
                violationSince.computeIfAbsent(rule.getId(), k -> System.currentTimeMillis());
                long since = violationSince.get(rule.getId());
                if (System.currentTimeMillis() - since >= rule.getDurationSeconds() * 1000L) {
                    if (!hasActiveEvent(rule.getId())) {
                        boolean silenced = repository.isSilenced(rule.getId());
                        AlertEvent event = repository.recordTrigger(rule, value);
                        if (silenced) {
                            event.setStatus("silenced");
                            log.info("Alert triggered but silenced: {}", rule.getName());
                        } else {
                            log.warn("Alert triggered: {} ({} {} {}, actual={})",
                                    rule.getName(), rule.getMetric(), rule.getOperator(), rule.getThreshold(), value);
                            pushDispatcher.pushAlertEvent(event);
                        }
                    }
                }
            } else {
                if (violationSince.remove(rule.getId()) != null) {
                    repository.resolveEvent(rule.getId());
                }
            }
        }
    }

    private boolean evaluateCondition(double value, String operator, double threshold) {
        switch (operator) {
            case ">": return value > threshold;
            case ">=": return value >= threshold;
            case "<": return value < threshold;
            case "<=": return value <= threshold;
            case "==": return value == threshold;
            default: return false;
        }
    }

    private boolean hasActiveEvent(String ruleId) {
        for (AlertEvent e : repository.listEvents(true)) {
            if (e.getRuleId().equals(ruleId)) return true;
        }
        return false;
    }

    private Map<String, Double> collectMetrics() {
        Map<String, Double> m = new HashMap<>();
        try {
            m.put("connections.active", (double) MqttProtocolHandler.metricConnectionsActive());
            m.put("connect.accepted.total", (double) MqttProtocolHandler.metricConnectAcceptedTotal());
            m.put("connect.rejected.total", (double) MqttProtocolHandler.metricConnectRejectedTotal());
            m.put("publish.in.total", (double) MqttProtocolHandler.metricPublishInTotal());
            m.put("publish.out.total", (double) MqttProtocolHandler.metricPublishOutTotal());
            m.put("acl.subscribe.deny.total", (double) MqttProtocolHandler.metricAclSubscribeDenyTotal());
            m.put("acl.publish.deny.total", (double) MqttProtocolHandler.metricAclPublishDenyTotal());
        } catch (Exception e) {
            log.debug("Failed to collect MQTT metrics: {}", e.getMessage());
        }
        try {
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            long used = memBean.getHeapMemoryUsage().getUsed();
            long max = memBean.getHeapMemoryUsage().getMax();
            m.put("heap.usage.percent", max > 0 ? (double) used * 100 / max : 0);
            m.put("heap.used.bytes", (double) used);
            m.put("heap.max.bytes", (double) max);
        } catch (Exception e) {
            log.debug("Failed to collect heap metrics: {}", e.getMessage());
        }
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOs = (com.sun.management.OperatingSystemMXBean) osBean;
                double procCpu = sunOs.getProcessCpuLoad();
                double sysCpu = sunOs.getSystemCpuLoad();
                m.put("cpu.process.percent", procCpu < 0 ? 0 : procCpu * 100);
                m.put("cpu.system.percent", sysCpu < 0 ? 0 : sysCpu * 100);
            }
            m.put("cpu.system.load", osBean.getSystemLoadAverage() < 0 ? 0 : osBean.getSystemLoadAverage());
        } catch (Exception e) {
            log.debug("Failed to collect CPU metrics: {}", e.getMessage());
        }
        return m;
    }
}
