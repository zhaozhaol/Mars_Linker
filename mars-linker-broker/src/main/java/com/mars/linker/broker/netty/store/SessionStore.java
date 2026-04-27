package com.mars.linker.broker.netty.store;

import java.util.Map;

/**
 * Session 持久化存储抽象。
 */
public interface SessionStore extends AutoCloseable {

    /**
     * 加载全部 session（实现需自行做版本/header 校验）。
     */
    public Map<String, SessionService.Session> loadAll();

    /**
     * 持久化全部 session（实现需保证尽量原子替换）。
     */
    public void persistAll(Map<String, SessionService.Session> sessions);

    /**
     * 删除持久化文件（仅测试辅助）。
     */
    public void deleteIfExists();

    default boolean supportsIncrementalPersist() {
        return false;
    }

    default void persistClient(SessionService.Session session) {
        // fallback to full persist on stores without incremental support
        if (session == null || session.clientId == null) {
            return;
        }
        Map<String, SessionService.Session> all = loadAll();
        all.put(session.clientId, session);
        persistAll(all);
    }

    default void deleteClient(String clientId) {
        if (clientId == null) {
            return;
        }
        Map<String, SessionService.Session> all = loadAll();
        all.remove(clientId);
        persistAll(all);
    }

    @Override
    default void close() {
        // no-op by default
    }
}
