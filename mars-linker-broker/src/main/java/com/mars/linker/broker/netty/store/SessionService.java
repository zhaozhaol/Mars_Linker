package com.mars.linker.broker.netty.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
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
        public final Map<String, Integer> subscriptionsQos = new ConcurrentHashMap<>();
        public final Queue<QueuedMessage> offlineQueue = new ConcurrentLinkedQueue<>();

        public Session(String clientId) {
            this.clientId = clientId;
        }
    }

    public static final class QueuedMessage {
        public final String topic;
        public final byte[] payload;
        public final boolean retain;
        public final int qos;

        public QueuedMessage(String topic, byte[] payload, boolean retain, int qos) {
            this.topic = topic;
            this.payload = payload;
            this.retain = retain;
            this.qos = qos;
        }
    }

    private final SessionStore store;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService persistExecutor;
    private final AtomicBoolean persistDirty = new AtomicBoolean(false);
    private final Object persistTaskLock = new Object();
    private volatile ScheduledFuture<?> pendingPersistTask;

    public static final SessionService INSTANCE = new SessionService(
            new FileSessionStore(java.nio.file.Paths.get("data", "session-store.tsv"))
    );

    private SessionService(SessionStore store) {
        this.store = store;
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "session-store-persist");
            t.setDaemon(true);
            return t;
        };
        this.persistExecutor = Executors.newSingleThreadScheduledExecutor(tf);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAndFlush, "session-store-shutdown"));
        this.sessions.putAll(store.loadAll());
        if (!sessions.isEmpty()) {
            log.info("已加载持久化会话 {} 个", sessions.size());
        }
    }

    public static SessionService create(SessionStore store) {
        if (store == null) {
            return INSTANCE;
        }
        return new SessionService(store);
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
        persist();
    }

    public Collection<Session> allSessions() {
        return sessions.values();
    }

    public void persist() {
        persistDirty.set(true);
        schedulePersistIfNeeded();
    }

    private void flushPersistIfDirty() {
        try {
            if (!persistDirty.compareAndSet(true, false)) {
                return;
            }
            store.persistAll(sessions);
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
        store.deleteIfExists();
    }

    public synchronized void reloadForTests() {
        flushPersistNow();
        sessions.clear();
        sessions.putAll(store.loadAll());
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
}
