package com.mars.linker.broker.netty.store;

import com.mars.linker.broker.config.MarsLinkerMqttBrokerProperties;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 根据配置选择持久化存储实现，并支持启动迁移。
 */
public final class StoreFactory {
    private static final Logger log = LoggerFactory.getLogger(StoreFactory.class);

    private StoreFactory() {
    }

    public static SessionStore createSessionStore(MarsLinkerMqttBrokerProperties p) {
        if (p != null && !p.isStorageEnabled()) {
            log.info("持久化已关闭，SessionStore 使用 no-op 内存模式");
            return new NoopSessionStore();
        }
        String mode = normalizeMode(p);
        log.info("SessionStore 模式: {}", mode);
        if ("redis".equals(mode)) {
            RedisURI redisUri = requireRedisUri(p);
            return new RedisSessionStore(redisUri, p.getStorageRedisKeyPrefix());
        }
        if ("db".equals(mode)) {
            requireDbConfig(p);
            return new DbSessionStore(
                    p.getStorageDbJdbcUrl(),
                    p.getStorageDbUsername(),
                    p.getStorageDbPassword(),
                    p.getStorageDbSchema(),
                    p.getStorageDbTablePrefix()
            );
        }
        return new FileSessionStore(Paths.get(defaultIfBlank(
                p == null ? null : p.getSessionStoreFilePath(),
                "data/session-store.tsv"
        )));
    }

    public static RetainStore createRetainStore(MarsLinkerMqttBrokerProperties p) {
        if (p != null && !p.isStorageEnabled()) {
            log.info("持久化已关闭，RetainStore 使用 no-op 内存模式");
            return new NoopRetainStore();
        }
        String mode = normalizeMode(p);
        log.info("RetainStore 模式: {}", mode);
        if ("redis".equals(mode)) {
            RedisURI redisUri = requireRedisUri(p);
            return new RedisRetainStore(redisUri, p.getStorageRedisKeyPrefix());
        }
        if ("db".equals(mode)) {
            requireDbConfig(p);
            return new DbRetainStore(
                    p.getStorageDbJdbcUrl(),
                    p.getStorageDbUsername(),
                    p.getStorageDbPassword(),
                    p.getStorageDbSchema(),
                    p.getStorageDbTablePrefix()
            );
        }
        Path path = Paths.get(defaultIfBlank(
                p == null ? null : p.getRetainStoreFilePath(),
                "data/retain-store.tsv"
        ));
        return new FileRetainStore(path);
    }

    /**
     * 启动迁移：当 migrateOnStartup=true 且 mode!=file 时，从 file 存储加载一次数据并写入目标存储。
     * <p>
     * 幂等性保证：SessionStore.persistAll 为全量替换语义（先清后写），RetainStore.put 为 upsert 语义（同 topic 覆盖）。
     * </p>
     */
    public static void migrateIfNeeded(MarsLinkerMqttBrokerProperties p,
                                SessionStore targetSessionStore,
                                RetainStore targetRetainStore) {
        if (p == null || !p.isStorageEnabled() || !p.isStorageMigrateOnStartup()) {
            return;
        }
        String mode = normalizeMode(p);
        if ("file".equals(mode)) {
            log.info("mode=file，跳过启动迁移");
            return;
        }
        log.info("开始启动迁移: file -> {}", mode);

        // Session 迁移
        String sessionPath = defaultIfBlank(p.getSessionStoreFilePath(), "data/session-store.tsv");
        FileSessionStore fileSessionStore = new FileSessionStore(Paths.get(sessionPath));
        Map<String, SessionService.Session> sessions = fileSessionStore.loadAll();
        targetSessionStore.persistAll(sessions);

        // Retain 迁移
        String retainPath = defaultIfBlank(p.getRetainStoreFilePath(), "data/retain-store.tsv");
        FileRetainStore fileRetainStore = new FileRetainStore(Paths.get(retainPath));
        List<RetainStore.RetainedMessage> retains = fileRetainStore.list();
        for (RetainStore.RetainedMessage m : retains) {
            targetRetainStore.put(m.topic, m.payload, m.qos);
        }

        log.info("启动迁移完成: {} 个 session, {} 条 retain 消息", sessions.size(), retains.size());
    }

    private static String normalizeMode(MarsLinkerMqttBrokerProperties p) {
        String mode = p == null ? null : p.getStorageMode();
        if (mode == null || mode.trim().isEmpty()) {
            return "file";
        }
        String m = mode.trim().toLowerCase();
        if ("file".equals(m) || "redis".equals(m) || "db".equals(m)) {
            return m;
        }
        log.warn("未知 storageMode={}，将按 file 处理", mode);
        return "file";
    }

    private static String defaultIfBlank(String s, String d) {
        return (s == null || s.trim().isEmpty()) ? d : s.trim();
    }

    private static RedisURI requireRedisUri(MarsLinkerMqttBrokerProperties p) {
        if (p == null || isBlank(p.getStorageRedisAddress())) {
            throw new IllegalStateException("storageMode=redis 时必须配置 storageRedisAddress");
        }
        RedisURI uri = RedisURI.create(p.getStorageRedisAddress().trim());
        uri.setDatabase(Math.max(0, p.getStorageRedisDatabase()));
        uri.setTimeout(Duration.ofMillis(Math.max(1L, p.getStorageRedisTimeoutMs())));
        if (!isBlank(p.getStorageRedisPassword())) {
            uri.setPassword(p.getStorageRedisPassword().toCharArray());
        }
        return uri;
    }

    private static void requireDbConfig(MarsLinkerMqttBrokerProperties p) {
        if (p == null || isBlank(p.getStorageDbJdbcUrl())) {
            throw new IllegalStateException("storageMode=db 时必须配置 storageDbJdbcUrl");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
