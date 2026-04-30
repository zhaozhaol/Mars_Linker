package com.mars.linker.broker.ui.config;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RuntimeConfigService {

    private static final Set<String> STATIC_FIELDS = Set.of(
            "enabled", "tcpPort", "storageMode", "persistMode",
            "collectionBufferSize", "monitoringThreadPoolSize", "pushThreadPoolSize",
            "maxHistoryMemoryMB", "maxApiQueriesPerSecond", "maxPushConnections"
    );

    private final MarsLinkerUiProperties uiProperties;
    private final AtomicReference<String> collectMode = new AtomicReference<>("normal");
    private final AtomicLong monitorRefreshMs;
    private final AtomicBoolean connectionEnabled;
    private final AtomicBoolean messageEnabled;
    private final AtomicBoolean subscriptionEnabled;
    private final AtomicBoolean systemEnabled;
    private final AtomicBoolean alertEvaluationEnabled;
    private final AtomicReference<Double> connectionSampleRate;
    private final AtomicReference<Double> messageSampleRate;
    private final AtomicReference<Double> subscriptionSampleRate;
    private final AtomicReference<Double> systemSampleRate;
    private final AtomicLong aggregationWindowMs;

    public RuntimeConfigService(MarsLinkerUiProperties uiProperties) {
        this.uiProperties = uiProperties;
        this.monitorRefreshMs = new AtomicLong(uiProperties.getMonitorRefreshMs());
        this.connectionEnabled = new AtomicBoolean(uiProperties.isConnectionEnabled());
        this.messageEnabled = new AtomicBoolean(uiProperties.isMessageEnabled());
        this.subscriptionEnabled = new AtomicBoolean(uiProperties.isSubscriptionEnabled());
        this.systemEnabled = new AtomicBoolean(uiProperties.isSystemEnabled());
        this.alertEvaluationEnabled = new AtomicBoolean(uiProperties.isAlertEvaluationEnabled());
        this.connectionSampleRate = new AtomicReference<>(uiProperties.getConnectionSampleRate());
        this.messageSampleRate = new AtomicReference<>(uiProperties.getMessageSampleRate());
        this.subscriptionSampleRate = new AtomicReference<>(uiProperties.getSubscriptionSampleRate());
        this.systemSampleRate = new AtomicReference<>(uiProperties.getSystemSampleRate());
        this.aggregationWindowMs = new AtomicLong(uiProperties.getAggregationWindowMs());
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", uiProperties.isEnabled());
        config.put("collectMode", collectMode.get());
        config.put("monitorRefreshMs", monitorRefreshMs.get());
        config.put("collectionBufferSize", uiProperties.getCollectionBufferSize());
        config.put("connectionEnabled", connectionEnabled.get());
        config.put("messageEnabled", messageEnabled.get());
        config.put("subscriptionEnabled", subscriptionEnabled.get());
        config.put("systemEnabled", systemEnabled.get());
        config.put("alertEvaluationEnabled", alertEvaluationEnabled.get());
        config.put("connectionSampleRate", connectionSampleRate.get());
        config.put("messageSampleRate", messageSampleRate.get());
        config.put("subscriptionSampleRate", subscriptionSampleRate.get());
        config.put("systemSampleRate", systemSampleRate.get());
        config.put("aggregationWindowMs", aggregationWindowMs.get());
        return config;
    }

    public Map<String, Object> update(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return snapshot();
        }
        for (String key : updates.keySet()) {
            if (STATIC_FIELDS.contains(key)) {
                throw new IllegalArgumentException("Cannot modify static config field: " + key);
            }
        }
        Object mode = updates.get("collectMode");
        if (mode instanceof String) {
            String trimmed = ((String) mode).trim();
            if (!trimmed.isEmpty()) {
                collectMode.set(trimmed);
            }
        }
        Object refresh = updates.get("monitorRefreshMs");
        if (refresh instanceof Number) {
            long value = ((Number) refresh).longValue();
            if (value > 0) {
                monitorRefreshMs.set(value);
            }
        }
        Object connEnabled = updates.get("connectionEnabled");
        if (connEnabled instanceof Boolean) connectionEnabled.set((Boolean) connEnabled);
        Object msgEnabled = updates.get("messageEnabled");
        if (msgEnabled instanceof Boolean) messageEnabled.set((Boolean) msgEnabled);
        Object subEnabled = updates.get("subscriptionEnabled");
        if (subEnabled instanceof Boolean) subscriptionEnabled.set((Boolean) subEnabled);
        Object sysEnabled = updates.get("systemEnabled");
        if (sysEnabled instanceof Boolean) systemEnabled.set((Boolean) sysEnabled);
        Object alertEvalEnabled = updates.get("alertEvaluationEnabled");
        if (alertEvalEnabled instanceof Boolean) alertEvaluationEnabled.set((Boolean) alertEvalEnabled);

        Object connRate = updates.get("connectionSampleRate");
        if (connRate instanceof Number) connectionSampleRate.set(clampSampleRate(((Number) connRate).doubleValue()));
        Object msgRate = updates.get("messageSampleRate");
        if (msgRate instanceof Number) messageSampleRate.set(clampSampleRate(((Number) msgRate).doubleValue()));
        Object subRate = updates.get("subscriptionSampleRate");
        if (subRate instanceof Number) subscriptionSampleRate.set(clampSampleRate(((Number) subRate).doubleValue()));
        Object sysRate = updates.get("systemSampleRate");
        if (sysRate instanceof Number) systemSampleRate.set(clampSampleRate(((Number) sysRate).doubleValue()));

        Object aggWindow = updates.get("aggregationWindowMs");
        if (aggWindow instanceof Number) {
            long value = ((Number) aggWindow).longValue();
            if (value > 0) aggregationWindowMs.set(value);
        }
        return snapshot();
    }

    public boolean isAlertEvaluationEnabled() {
        return alertEvaluationEnabled.get();
    }

    public boolean isConnectionEnabled() {
        return connectionEnabled.get();
    }

    public boolean isMessageEnabled() {
        return messageEnabled.get();
    }

    public boolean isSubscriptionEnabled() {
        return subscriptionEnabled.get();
    }

    public boolean isSystemEnabled() {
        return systemEnabled.get();
    }

    public double getConnectionSampleRate() {
        return connectionSampleRate.get();
    }

    public double getMessageSampleRate() {
        return messageSampleRate.get();
    }

    public double getSubscriptionSampleRate() {
        return subscriptionSampleRate.get();
    }

    public double getSystemSampleRate() {
        return systemSampleRate.get();
    }

    public long getAggregationWindowMs() {
        return aggregationWindowMs.get();
    }

    public long getMonitorRefreshMs() {
        return monitorRefreshMs.get();
    }

    private double clampSampleRate(double rate) {
        return Math.min(1.0, Math.max(0.0, rate));
    }
}
