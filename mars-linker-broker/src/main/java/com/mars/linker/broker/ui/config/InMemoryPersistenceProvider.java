package com.mars.linker.broker.ui.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryPersistenceProvider implements ConfigPersistenceProvider {

    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    @Override
    public void save(String key, Map<String, Object> config) {
        store.put(key, new ConcurrentHashMap<>(config));
    }

    @Override
    public Map<String, Object> load(String key) {
        Map<String, Object> data = store.get(key);
        return data != null ? new ConcurrentHashMap<>(data) : Map.of();
    }

    @Override
    public boolean exists(String key) {
        return store.containsKey(key);
    }
}
