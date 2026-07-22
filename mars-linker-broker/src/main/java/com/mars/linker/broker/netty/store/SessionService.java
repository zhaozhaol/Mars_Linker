package com.mars.linker.broker.netty.store;

import com.mars.linker.broker.netty.protocol.TopicFilterSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 会话与离线队列管理（最小实现），并负责持久化协调。
 */
public final class SessionService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final long PERSIST_DEBOUNCE_MS = 200L;
    private static final long FULL_PERSIST_INTERVAL_MS = 5 * 60 * 1000L; // 5 分钟全量写一次

    public static final class Session {
        public final String clientId;
        private final Map<String, Integer> ownedSubscriptionsQos = new ConcurrentHashMap<>();
        private volatile Map<String, Integer> subscriptionsQosRef = ownedSubscriptionsQos;
        public final Queue<QueuedMessage> offlineQueue = new ConcurrentLinkedQueue<>();
        volatile long lastActivityMs;

        public Session(String clientId) {
            this.clientId = clientId;
            this.lastActivityMs = System.currentTimeMillis();
        }

        public Map<String, Integer> subscriptionsQos() {
            return subscriptionsQosRef;
        }

        public void bindChannelSubscriptions(Map<String, Integer> channelQosMap) {
            channelQosMap.putAll(ownedSubscriptionsQos);
            this.subscriptionsQosRef = channelQosMap;
        }

        public void unbindChannelSubscriptions() {
            Map<String, Integer> current = subscriptionsQosRef;
            if (current != ownedSubscriptionsQos) {
                ownedSubscriptionsQos.clear();
                ownedSubscriptionsQos.putAll(current);
                this.subscriptionsQosRef = ownedSubscriptionsQos;
            }
        }

        public long lastActivityMs() {
            return lastActivityMs;
        }

        public void touchActivity() {
            this.lastActivityMs = System.currentTimeMillis();
        }

        public void touchActivity(long ts) {
            this.lastActivityMs = ts;
        }
    }

    public static final class QueuedMessage {
        public final String topic;
        public final byte[] payload;
        public final boolean retain;
        public final int qos;
        public final long createdAtMs;

        public QueuedMessage(String topic, byte[] payload, boolean retain, int qos) {
            this(topic, payload, retain, qos, System.currentTimeMillis());
        }

        public QueuedMessage(String topic, byte[] payload, boolean retain, int qos, long createdAtMs) {
            this.topic = topic;
            this.payload = payload;
            this.retain = retain;
            this.qos = qos;
            this.createdAtMs = createdAtMs;
        }
    }

    private final SessionStore store;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>(16384);
    private final ConcurrentHashMap<String, Session> offlineSessions = new ConcurrentHashMap<>(4096);
    private final ConcurrentHashMap<String, Set<String>> filterToOfflineClientIds = new ConcurrentHashMap<>();
    private final int offlineMaxMessages;
    private final long offlineTtlMs;
    private final long sessionTtlMs;
    private final ScheduledExecutorService persistExecutor;
    private final AtomicBoolean persistDirty = new AtomicBoolean(false);
    private final Object persistTaskLock = new Object();
    private volatile ScheduledFuture<?> pendingPersistTask;
    private final Set<String> dirtyClientIds = ConcurrentHashMap.newKeySet();
    private final Set<String> removedClientIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean fullPersistDirty = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> sessionCleanupTask;
    private volatile ScheduledFuture<?> fullPersistTask;

    private SessionService(SessionStore store, int offlineMaxMessages, long offlineTtlMs, long sessionTtlMs) {
        this.store = store;
        this.offlineMaxMessages = offlineMaxMessages;
        this.offlineTtlMs = offlineTtlMs;
        this.sessionTtlMs = sessionTtlMs;
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "session-store-persist");
            t.setDaemon(true);
            return t;
        };
        this.persistExecutor = Executors.newSingleThreadScheduledExecutor(tf);
        this.sessions.putAll(store.loadAll());
        long now = System.currentTimeMillis();
        int expiredOnLoad = 0;
        int emptyShellOnLoad = 0;
        for (Session session : this.sessions.values()) {
            pruneOfflineQueue(session, now);
        }
        // 启动时清理过期会话 + 空壳会话（无订阅 + 无离线消息）
        for (Map.Entry<String, Session> entry : this.sessions.entrySet()) {
            Session s = entry.getValue();
            if (s == null) {
                continue;
            }
            boolean expired = sessionTtlMs > 0 && now - s.lastActivityMs > sessionTtlMs;
            boolean emptyShell = s.subscriptionsQos().isEmpty() && s.offlineQueue.isEmpty();
            if (expired || emptyShell) {
                this.sessions.remove(entry.getKey());
                if (expired) {
                    expiredOnLoad++;
                } else {
                    emptyShellOnLoad++;
                }
            }
        }
        if (expiredOnLoad > 0) {
            log.info("启动清理过期会话 {} 个（TTL={}ms）", expiredOnLoad, sessionTtlMs);
        }
        if (emptyShellOnLoad > 0) {
            log.info("启动清理空壳会话 {} 个（无订阅+无离线消息）", emptyShellOnLoad);
        }
        if (expiredOnLoad > 0 || emptyShellOnLoad > 0) {
            persist();
        }
        if (!sessions.isEmpty()) {
            log.info("已加载持久化会话 {} 个", sessions.size());
        }
        // 定时清理过期离线会话（每小时一次）
        if (sessionTtlMs > 0) {
            this.sessionCleanupTask = this.persistExecutor.scheduleAtFixedRate(
                    this::pruneExpiredSessions, 1, 1, TimeUnit.HOURS);
        }
        // 定时全量持久化（每 5 分钟一次，作为增量持久化的安全网 + 文件压缩）
        this.fullPersistTask = this.persistExecutor.scheduleAtFixedRate(() -> {
            fullPersistDirty.set(true);
            persistDirty.set(true);
            schedulePersistIfNeeded();
        }, FULL_PERSIST_INTERVAL_MS, FULL_PERSIST_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public static SessionService create(SessionStore store) {
        return create(store, 10_000, 7L * 24 * 60 * 60 * 1000, 30L * 24 * 60 * 60 * 1000);
    }

    public static SessionService create(SessionStore store, int offlineMaxMessages, long offlineTtlMs) {
        return create(store, offlineMaxMessages, offlineTtlMs, 30L * 24 * 60 * 60 * 1000);
    }

    public static SessionService create(SessionStore store, int offlineMaxMessages, long offlineTtlMs, long sessionTtlMs) {
        if (store == null) {
            store = new NoopSessionStore();
        }
        return new SessionService(store, offlineMaxMessages, offlineTtlMs, sessionTtlMs);
    }

    public Session getOrCreate(String clientId) {
        return sessions.computeIfAbsent(clientId, Session::new);
    }

    public Session get(String clientId) {
        return sessions.get(clientId);
    }

    public void remove(String clientId) {
        if (clientId == null) {
            return;
        }
        sessions.remove(clientId);
        removedClientIds.add(clientId);
        dirtyClientIds.remove(clientId);
        scheduleIncrementalPersist();
    }

    public Collection<Session> allSessions() {
        return sessions.values();
    }

    public Collection<Session> offlineSessions() {
        return offlineSessions.values();
    }

    public void markOffline(String clientId) {
        Session s = sessions.get(clientId);
        if (s != null && !s.subscriptionsQos().isEmpty()) {
            offlineSessions.put(clientId, s);
            for (String filter : s.subscriptionsQos().keySet()) {
                filterToOfflineClientIds.computeIfAbsent(filter, k -> ConcurrentHashMap.newKeySet()).add(clientId);
            }
        }
    }

    public void markOnline(String clientId) {
        Session s = offlineSessions.remove(clientId);
        if (s != null) {
            removeFromOfflineFilterIndex(clientId, s);
        }
    }

    private void removeFromOfflineFilterIndex(String clientId, Session s) {
        for (String filter : s.subscriptionsQos().keySet()) {
            filterToOfflineClientIds.computeIfPresent(filter, (k, v) -> {
                v.remove(clientId);
                return v.isEmpty() ? null : v;
            });
        }
    }

    public int offlineSessionCount() {
        return offlineSessions.size();
    }

    public void persist() {
        fullPersistDirty.set(true);
        persistDirty.set(true);
        schedulePersistIfNeeded();
    }

    public void persist(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            persist();
            return;
        }
        dirtyClientIds.add(clientId);
        removedClientIds.remove(clientId);
        persistDirty.set(true);
        schedulePersistIfNeeded();
    }

    /**
     * 仅调度增量刷盘，不设置 fullPersistDirty。
     * 用于 remove/pruneExpiredSessions 等只需增量更新的场景，避免全量写文件。
     */
    private void scheduleIncrementalPersist() {
        persistDirty.set(true);
        schedulePersistIfNeeded();
    }

    public boolean enqueueOfflineMessage(Session session, String topic, byte[] payload, boolean retain, int qos) {
        if (session == null || topic == null || payload == null) {
            return false;
        }
        byte[] copy = new byte[payload.length];
        System.arraycopy(payload, 0, copy, 0, payload.length);
        session.offlineQueue.add(new QueuedMessage(topic, copy, retain, qos, System.currentTimeMillis()));
        pruneOfflineQueue(session, System.currentTimeMillis());
        return true;
    }

    /**
     * 用离线 filter 索引快速查找匹配的离线会话并入队消息。
     * 复杂度 O(uniqueOfflineFilters) 而非 O(offlineSessions × subscriptions)。
     */
    public void enqueueForOfflineMatches(String topic, byte[] payload, boolean retain, int pubQos) {
        if (filterToOfflineClientIds.isEmpty()) {
            return;
        }
        Map<String, Integer> clientIdToMaxQos = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : filterToOfflineClientIds.entrySet()) {
            String filter = entry.getKey();
            if (!TopicFilterSupport.matchTopicFilter(filter, topic)) {
                continue;
            }
            Set<String> clientIds = entry.getValue();
            for (String clientId : clientIds) {
                Session session = sessions.get(clientId);
                if (session == null) {
                    continue;
                }
                Integer qos = session.subscriptionsQos().get(filter);
                if (qos == null) {
                    continue;
                }
                clientIdToMaxQos.merge(clientId, qos, Math::max);
            }
        }
        for (Map.Entry<String, Integer> entry : clientIdToMaxQos.entrySet()) {
            Session session = sessions.get(entry.getKey());
            if (session == null) {
                continue;
            }
            int eff = Math.max(Math.min(pubQos, entry.getValue()), 0);
            if (enqueueOfflineMessage(session, topic, payload, retain, eff)) {
                persist(session.clientId);
            }
        }
    }

    private void flushPersistIfDirty() {
        try {
            if (!persistDirty.compareAndSet(true, false)) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Session session : sessions.values()) {
                pruneOfflineQueue(session, now);
            }
            if (fullPersistDirty.getAndSet(false) || !store.supportsIncrementalPersist()) {
                store.persistAll(sessions);
                dirtyClientIds.clear();
                removedClientIds.clear();
                return;
            }
            if (dirtyClientIds.isEmpty() && removedClientIds.isEmpty()) {
                return;
            }
            // 批量增量持久化：单次 I/O 完成所有 dirty/removed 客户端的更新
            Map<String, Session> dirty = new HashMap<>();
            for (String clientId : dirtyClientIds) {
                Session s = sessions.get(clientId);
                if (s != null) {
                    dirty.put(clientId, s);
                }
            }
            Set<String> removed = new HashSet<>(removedClientIds);
            // dirtyClientIds 中 session 已不存在的也转入 removed
            for (String clientId : dirtyClientIds) {
                if (sessions.get(clientId) == null) {
                    removed.add(clientId);
                }
            }
            store.persistIncremental(dirty, removed, sessions);
            dirtyClientIds.clear();
            removedClientIds.clear();
        } finally {
            synchronized (persistTaskLock) {
                pendingPersistTask = null;
                if (persistDirty.get()) {
                    pendingPersistTask = persistExecutor.schedule(this::flushPersistIfDirty,
                            PERSIST_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    private void schedulePersistIfNeeded() {
        synchronized (persistTaskLock) {
            ScheduledFuture<?> task = pendingPersistTask;
            if (task != null && !task.isDone()) {
                return;
            }
            pendingPersistTask = persistExecutor.schedule(this::flushPersistIfDirty,
                    PERSIST_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void resetForTests() {
        flushPersistNow();
        sessions.clear();
        offlineSessions.clear();
        filterToOfflineClientIds.clear();
        dirtyClientIds.clear();
        removedClientIds.clear();
        fullPersistDirty.set(false);
        store.deleteIfExists();
    }

    public synchronized void reloadForTests() {
        flushPersistNow();
        sessions.clear();
        sessions.putAll(store.loadAll());
        long now = System.currentTimeMillis();
        for (Session session : sessions.values()) {
            pruneOfflineQueue(session, now);
        }
        dirtyClientIds.clear();
        removedClientIds.clear();
        fullPersistDirty.set(false);
    }

    public synchronized void flushPersistNow() {
        ScheduledFuture<?> task;
        synchronized (persistTaskLock) {
            task = pendingPersistTask;
            pendingPersistTask = null;
        }
        if (task != null) {
            task.cancel(false);
        }
        flushPersistIfDirty();
    }

    private void shutdownAndFlush() {
        try {
            ScheduledFuture<?> cleanup = sessionCleanupTask;
            if (cleanup != null) {
                cleanup.cancel(false);
            }
            ScheduledFuture<?> fullPersist = fullPersistTask;
            if (fullPersist != null) {
                fullPersist.cancel(false);
            }
            flushPersistNow();
            persistExecutor.shutdown();
            if (!persistExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                persistExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            persistExecutor.shutdownNow();
        } catch (RuntimeException e) {
            log.warn("session store 关闭刷盘失败", e);
        }
    }

    private void pruneOfflineQueue(Session session, long nowMs) {
        if (session == null) {
            return;
        }
        if (offlineTtlMs > 0) {
            while (true) {
                QueuedMessage head = session.offlineQueue.peek();
                if (head == null) {
                    break;
                }
                if (nowMs - head.createdAtMs <= offlineTtlMs) {
                    break;
                }
                session.offlineQueue.poll();
            }
        }
        if (offlineMaxMessages > 0) {
            while (session.offlineQueue.size() > offlineMaxMessages) {
                session.offlineQueue.poll();
            }
        }
    }

    /**
     * 定时清理过期离线会话（仅清理 offlineSessions 中的，不影响在线会话）。
     * 同时清理 sessions 中的空壳会话（无订阅 + 无离线消息）。
     */
    private void pruneExpiredSessions() {
        if (sessionTtlMs <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        int expiredOffline = 0;
        int emptyShell = 0;
        // 1. 清理 offlineSessions 中超 TTL 的会话
        for (Map.Entry<String, Session> entry : offlineSessions.entrySet()) {
            Session s = entry.getValue();
            if (s == null) {
                continue;
            }
            if (now - s.lastActivityMs > sessionTtlMs) {
                sessions.remove(entry.getKey());
                offlineSessions.remove(entry.getKey());
                removeFromOfflineFilterIndex(entry.getKey(), s);
                removedClientIds.add(entry.getKey());
                dirtyClientIds.remove(entry.getKey());
                expiredOffline++;
            }
        }
        // 2. 清理 sessions 中的空壳会话（无订阅 + 无离线消息，不在 offlineSessions 中）
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session s = entry.getValue();
            if (s == null) {
                continue;
            }
            if (!offlineSessions.containsKey(entry.getKey())
                    && s.subscriptionsQos().isEmpty()
                    && s.offlineQueue.isEmpty()) {
                sessions.remove(entry.getKey());
                removedClientIds.add(entry.getKey());
                emptyShell++;
            }
        }
        int total = expiredOffline + emptyShell;
        if (total > 0) {
            if (expiredOffline > 0) {
                log.info("定时清理过期离线会话 {} 个（TTL={}ms）", expiredOffline, sessionTtlMs);
            }
            if (emptyShell > 0) {
                log.info("定时清理空壳会话 {} 个（无订阅+无离线消息）", emptyShell);
            }
            scheduleIncrementalPersist();
        }
    }
}
