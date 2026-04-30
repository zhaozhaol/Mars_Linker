package com.mars.linker.broker.netty.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 关闭持久化时使用的空实现：仅内存运行，不做任何落盘。
 */
public final class NoopSessionStore implements SessionStore {
    @Override
    public Map<String, SessionService.Session> loadAll() {
        return new ConcurrentHashMap<>();
    }

    @Override
    public void persistAll(Map<String, SessionService.Session> sessions) {
        // no-op
    }

    @Override
    public void deleteIfExists() {
        // no-op
    }

    @Override
    public boolean supportsIncrementalPersist() {
        return true;
    }

    @Override
    public void persistClient(SessionService.Session session) {
        // no-op
    }

    @Override
    public void deleteClient(String clientId) {
        // no-op
    }
}
