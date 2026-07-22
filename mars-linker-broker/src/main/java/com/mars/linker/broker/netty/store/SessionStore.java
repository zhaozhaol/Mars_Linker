package com.mars.linker.broker.netty.store;

import java.util.Map;
import java.util.Set;

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

    /**
     * 批量增量持久化：将 dirty 会话更新到存储、将 removed 会话从存储删除。
     * 实现应尽量在单次 I/O 中完成所有变更，避免逐客户端读写。
     * 默认回退到全量持久化。
     *
     * @param dirtySessions  需要更新的会话（clientId → Session）
     * @param removedClientIds 需要删除的 clientId 集合
     * @param allSessions    当前内存中的全部会话（供回退全量写使用）
     */
    default void persistIncremental(Map<String, SessionService.Session> dirtySessions,
                                     Set<String> removedClientIds,
                                     Map<String, SessionService.Session> allSessions) {
        persistAll(allSessions);
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
