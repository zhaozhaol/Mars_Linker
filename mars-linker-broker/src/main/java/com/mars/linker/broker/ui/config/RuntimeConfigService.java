package com.mars.linker.broker.ui.config;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理 UI 模块的运行时配置（内存态）。
 */
@Service
public class RuntimeConfigService {

    private final MarsLinkerUiProperties uiProperties;
    private final AtomicReference<String> collectMode = new AtomicReference<>("normal");
    private final AtomicLong monitorRefreshMs;

    public RuntimeConfigService(MarsLinkerUiProperties uiProperties) {
        this.uiProperties = uiProperties;
        this.monitorRefreshMs = new AtomicLong(uiProperties.getMonitorRefreshMs());
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", uiProperties.isEnabled());
        config.put("collectMode", collectMode.get());
        config.put("monitorRefreshMs", monitorRefreshMs.get());
        config.put("collectionBufferSize", uiProperties.getCollectionBufferSize());
        return config;
    }

    public Map<String, Object> update(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return snapshot();
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
        return snapshot();
    }
}
