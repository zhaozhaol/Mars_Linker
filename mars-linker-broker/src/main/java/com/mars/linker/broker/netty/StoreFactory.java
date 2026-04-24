package com.mars.linker.broker.netty;

import com.mars.linker.broker.config.MarsLinkerMqttBrokerProperties;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * 根据配置选择持久化存储实现。
 */
final class StoreFactory {
    private static final Logger log = LoggerFactory.getLogger(StoreFactory.class);

    private StoreFactory() {
    }

    static SessionStore createSessionStore(MarsLinkerMqttBrokerProperties p) {
        String mode = normalizeMode(p);
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

    static RetainStore createRetainStore(MarsLinkerMqttBrokerProperties p) {
        String mode = normalizeMode(p);
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

