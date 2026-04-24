package com.mars.linker.broker.netty.store;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 版 SessionStore（当前以全量快照语义对齐文件实现）。
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
        this.cmd.ping(); // fail-fast: 验证 Redis 连接可用
    }

    @Override
    public synchronized Map<String, SessionService.Session> loadAll() {
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
                            s.subscriptionsQos.put(e.getKey(), qos);
                        }
                    } catch (RuntimeException ignored) {
                        // skip invalid line
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
    public synchronized void persistAll(Map<String, SessionService.Session> sessions) {
        try {
            String indexKey = k("sess:index");
            Set<String> oldIds = cmd.smembers(indexKey);
            for (String oldId : oldIds) {
                cmd.del(k("sess:" + oldId + ":subs"));
                cmd.del(k("sess:" + oldId + ":offline"));
            }
            cmd.del(indexKey);

            for (SessionService.Session s : sessions.values()) {
                if (s == null || s.clientId == null || s.clientId.isEmpty()) {
                    continue;
                }
                cmd.sadd(indexKey, s.clientId);
                String subKey = k("sess:" + s.clientId + ":subs");
                Map<String, String> subMap = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> sub : s.subscriptionsQos.entrySet()) {
                    subMap.put(sub.getKey(), String.valueOf(sub.getValue() == null ? 0 : sub.getValue()));
                }
                if (!subMap.isEmpty()) {
                    cmd.hmset(subKey, subMap);
                }
                String offlineKey = k("sess:" + s.clientId + ":offline");
                for (SessionService.QueuedMessage q : s.offlineQueue) {
                    String line = encodeOffline(q);
                    if (line != null) {
                        cmd.rpush(offlineKey, line);
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("RedisSessionStore persistAll 失败", e);
        }
    }

    @Override
    public synchronized void deleteIfExists() {
        try {
            String indexKey = k("sess:index");
            Set<String> ids = cmd.smembers(indexKey);
            for (String id : ids) {
                cmd.del(k("sess:" + id + ":subs"));
                cmd.del(k("sess:" + id + ":offline"));
            }
            cmd.del(indexKey);
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
        return q.topic + "\t" + q.qos + "\t" + (q.retain ? "1" : "0") + "\t"
                + Base64.getEncoder().encodeToString(q.payload);
    }

    private static SessionService.QueuedMessage decodeOffline(String line) {
        if (line == null) {
            return null;
        }
        String[] p = line.split("\t", 4);
        if (p.length != 4) {
            return null;
        }
        try {
            String topic = p[0];
            int qos = Integer.parseInt(p[1]);
            boolean retain = "1".equals(p[2]);
            byte[] payload = Base64.getDecoder().decode(p[3].getBytes(StandardCharsets.UTF_8));
            return new SessionService.QueuedMessage(topic, payload, retain, qos);
        } catch (RuntimeException e) {
            return null;
        }
    }
}

