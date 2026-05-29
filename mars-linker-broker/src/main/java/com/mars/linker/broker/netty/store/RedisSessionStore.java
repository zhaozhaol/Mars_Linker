package com.mars.linker.broker.netty.store;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 版 SessionStore（全量快照语义）。
 * <p>
 * 线程安全策略：Lettuce 单连接的同步 API 天然线程安全，无需 synchronized。
 * 单连接在 Lettuce 中通过内部锁保证命令顺序执行，足以满足 Broker 场景。
 * </p>
 */
public final class RedisSessionStore implements SessionStore {
    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> cmd;
    private final String keyPrefix;

    public RedisSessionStore(RedisURI redisUri, String keyPrefix) {
        this.client = RedisClient.create(redisUri);
        this.connection = client.connect();
        this.cmd = connection.sync();
        this.keyPrefix = keyPrefix == null || keyPrefix.trim().isEmpty() ? "ml" : keyPrefix.trim();
        this.cmd.ping();
        log.info("RedisSessionStore 初始化完成（Lettuce 单连接，线程安全）");
    }

    @Override
    public boolean supportsIncrementalPersist() {
        return true;
    }

    @Override
    public Map<String, SessionService.Session> loadAll() {
        Map<String, SessionService.Session> out = new ConcurrentHashMap<>();
        try {
            String indexKey = k("sess:index");
            Set<String> clientIds = cmd.smembers(indexKey);
            for (String clientId : clientIds) {
                if (clientId == null || clientId.isEmpty()) {
                    continue;
                }
                SessionService.Session s = new SessionService.Session(clientId);
                Map<String, String> subs = cmd.hgetall(k("sess:" + clientId + ":subs"));
                for (Map.Entry<String, String> e : subs.entrySet()) {
                    try {
                        int qos = Integer.parseInt(e.getValue());
                        if (qos >= 0 && qos <= 2) {
                            s.subscriptionsQos().put(e.getKey(), qos);
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
                List<String> offline = cmd.lrange(k("sess:" + clientId + ":offline"), 0, -1);
                for (String line : offline) {
                    SessionService.QueuedMessage q = decodeOffline(line);
                    if (q != null) {
                        s.offlineQueue.add(q);
                    }
                }
                out.put(clientId, s);
            }
        } catch (RuntimeException e) {
            log.warn("RedisSessionStore loadAll 失败", e);
        }
        return out;
    }

    @Override
    public void persistAll(Map<String, SessionService.Session> sessions) {
        try {
            String indexKey = k("sess:index");
            Set<String> oldIds = cmd.smembers(indexKey);
            if (!oldIds.isEmpty()) {
                List<String> keysToDelete = new ArrayList<>(oldIds.size() * 2 + 1);
                for (String oldId : oldIds) {
                    keysToDelete.add(k("sess:" + oldId + ":subs"));
                    keysToDelete.add(k("sess:" + oldId + ":offline"));
                }
                keysToDelete.add(indexKey);
                cmd.del(keysToDelete.toArray(new String[0]));
            } else {
                cmd.del(indexKey);
            }

            for (SessionService.Session s : sessions.values()) {
                if (s == null || s.clientId == null || s.clientId.isEmpty()) {
                    continue;
                }
                cmd.sadd(indexKey, s.clientId);
                String subKey = k("sess:" + s.clientId + ":subs");
                Map<String, String> subMap = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> sub : s.subscriptionsQos().entrySet()) {
                    subMap.put(sub.getKey(), String.valueOf(sub.getValue() == null ? 0 : sub.getValue()));
                }
                if (!subMap.isEmpty()) {
                    cmd.hmset(subKey, subMap);
                }
                String offlineKey = k("sess:" + s.clientId + ":offline");
                List<String> encoded = new ArrayList<>(s.offlineQueue.size());
                for (SessionService.QueuedMessage q : s.offlineQueue) {
                    String line = encodeOffline(q);
                    if (line != null) {
                        encoded.add(line);
                    }
                }
                if (!encoded.isEmpty()) {
                    cmd.rpush(offlineKey, encoded.toArray(new String[0]));
                }
            }
        } catch (RuntimeException e) {
            log.warn("RedisSessionStore persistAll 失败", e);
        }
    }

    @Override
    public void persistClient(SessionService.Session s) {
        if (s == null || s.clientId == null || s.clientId.isEmpty()) {
            return;
        }
        try {
            String indexKey = k("sess:index");
            String subKey = k("sess:" + s.clientId + ":subs");
            String offlineKey = k("sess:" + s.clientId + ":offline");
            cmd.sadd(indexKey, s.clientId);
            cmd.del(subKey);
            cmd.del(offlineKey);
            Map<String, String> subMap = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> sub : s.subscriptionsQos().entrySet()) {
                subMap.put(sub.getKey(), String.valueOf(sub.getValue() == null ? 0 : sub.getValue()));
            }
            if (!subMap.isEmpty()) {
                cmd.hmset(subKey, subMap);
            }
            List<String> encoded = new ArrayList<>(s.offlineQueue.size());
            for (SessionService.QueuedMessage q : s.offlineQueue) {
                String line = encodeOffline(q);
                if (line != null) {
                    encoded.add(line);
                }
            }
            if (!encoded.isEmpty()) {
                cmd.rpush(offlineKey, encoded.toArray(new String[0]));
            }
        } catch (RuntimeException e) {
            log.warn("RedisSessionStore persistClient 失败 clientId={}", s.clientId, e);
        }
    }

    @Override
    public void deleteClient(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return;
        }
        try {
            cmd.srem(k("sess:index"), clientId);
            cmd.del(k("sess:" + clientId + ":subs"), k("sess:" + clientId + ":offline"));
        } catch (RuntimeException e) {
            log.warn("RedisSessionStore deleteClient 失败 clientId={}", clientId, e);
        }
    }

    @Override
    public void deleteIfExists() {
        try {
            String indexKey = k("sess:index");
            Set<String> ids = cmd.smembers(indexKey);
            if (!ids.isEmpty()) {
                List<String> keysToDelete = new ArrayList<>(ids.size() * 2 + 1);
                for (String id : ids) {
                    keysToDelete.add(k("sess:" + id + ":subs"));
                    keysToDelete.add(k("sess:" + id + ":offline"));
                }
                keysToDelete.add(indexKey);
                cmd.del(keysToDelete.toArray(new String[0]));
            } else {
                cmd.del(indexKey);
            }
        } catch (RuntimeException e) {
            log.warn("RedisSessionStore deleteIfExists 失败", e);
        }
    }

    private String k(String suffix) {
        return keyPrefix + ":" + suffix;
    }

    private static String encodeOffline(SessionService.QueuedMessage q) {
        if (q == null || q.topic == null || q.payload == null) {
            return null;
        }
        return q.topic + "\t" + q.qos + "\t" + (q.retain ? "1" : "0") + "\t" + q.createdAtMs + "\t"
                + Base64.getEncoder().encodeToString(q.payload);
    }

    private static SessionService.QueuedMessage decodeOffline(String line) {
        if (line == null) {
            return null;
        }
        String[] p = line.split("\t", 5);
        if (p.length < 4) {
            return null;
        }
        try {
            String topic = p[0];
            int qos = Integer.parseInt(p[1]);
            boolean retain = "1".equals(p[2]);
            long createdAtMs;
            byte[] payload;
            if (p.length >= 5) {
                createdAtMs = Long.parseLong(p[3]);
                payload = Base64.getDecoder().decode(p[4].getBytes(StandardCharsets.UTF_8));
            } else {
                createdAtMs = System.currentTimeMillis();
                payload = Base64.getDecoder().decode(p[3].getBytes(StandardCharsets.UTF_8));
            }
            return new SessionService.QueuedMessage(topic, payload, retain, qos, createdAtMs);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (RuntimeException e) {
            log.debug("RedisSessionStore connection 关闭异常", e);
        }
        try {
            client.shutdown();
        } catch (RuntimeException e) {
            log.debug("RedisSessionStore client 关闭异常", e);
        }
    }
}
