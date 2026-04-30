package com.mars.linker.broker.ui.config;

import java.util.Map;

public interface ConfigPersistenceProvider {

    void save(String key, Map<String, Object> config);

    Map<String, Object> load(String key);

    boolean exists(String key);
}
