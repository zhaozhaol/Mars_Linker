package com.mars.linker.broker.netty.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
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

    public static final class Session {
        public final String clientId;
        private final Map<String, Integer> ownedSubscriptionsQos = new ConcurrentHashMap<>();
        private volatile Map<String, Integer> subscriptionsQosRef = ownedSubscriptionsQos;
        public final Queue<QueuedMessage> offlineQueue = new ConcurrentLinkedQueue<>();

        public Session(String clientId) {
            this.clientId = clientId;
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
    private final int offlineMaxMessages;
    private final long offlineTtlMs;
    private final ScheduledExecutorService persistExecutor;
    private final AtomicBoolean persistDirty = new AtomicBoolean(false);
    private final Object persistTaskLock = new Object();
    private volatile ScheduledFuture<?> pendingPersistTask;
    private final Set<String> dirtyClientIds = ConcurrentHashMap.newKeySet();
    private final Set<String> removedClientIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean fullPersistDirty = new AtomicBoolean(false);

    @Deprecated
    public static final SessionService INSTANCE = new SessionService(
            new FileSessionStore(java.nio.file.Paths.get("data", "session-store.tsv")),
            10_000,
            7L * 24 * 60 * 60 * 1000
    );

    private SessionService(SessionStore store, int offlineMaxMessages, long offlineTtlMs) {
        this.store = store;
        this.offlineMaxMessages = offlineMaxMessages;
        this.offlineTtlMs = offlineTtlMs;
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "session-store-persist");
            t.setDaemon(true);
            return t;
        };
        this.persistExecutor = Executors.newSingleThreadScheduledExecutor(tf);
        this.sessions.putAll(store.loadAll());
        long now = System.currentTimeMillis();
        for (Session session : this.sessions.values()) {
            pruneOfflineQueue(session, now);
        }
        if (!sessions.isEmpty()) {
            log.info("已加载持久化会话 {} 个", sessions.size());
        }
    }

    public static SessionService create(SessionStore store) {
        return create(store, 10_000, 7L * 24 * 60 * 60 * 1000);
    }

    public static SessionService create(SessionStore store, int offlineMaxMessages, long offlineTtlMs) {
        if (store == null) {
            store = new NoopSessionStore();
        }
        return new SessionService(store, offlineMaxMessages, offlineTtlMs);
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
        persist();
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
        }
    }

    public void markOnline(String clientId) {
        offlineSessions.remove(clientId);
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
            for (String clientId : removedClientIds) {
                store.deleteClient(clientId);
            }
            removedClientIds.clear();
            for (String clientId : dirtyClientIds) {
                Session s = sessions.get(clientId);
                if (s != null) {
                    store.persistClient(s);
                } else {
                    store.deleteClient(clientId);
                }
            }
            dirtyClientIds.clear();
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
}
