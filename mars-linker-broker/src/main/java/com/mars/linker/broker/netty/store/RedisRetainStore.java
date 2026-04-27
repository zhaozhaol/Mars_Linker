package com.mars.linker.broker.netty.store;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Redis 版 RetainStore。
 */
public final class RedisRetainStore implements RetainStore {
    private static final Logger log = LoggerFactory.getLogger(RedisRetainStore.class);

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> cmd;
    private final String retainKey;

    public RedisRetainStore(RedisURI redisUri, String keyPrefix) {
        this.client = RedisClient.create(redisUri);
        this.connection = this.client.connect();
        this.cmd = connection.sync();
        String p = keyPrefix == null || keyPrefix.trim().isEmpty() ? "ml" : keyPrefix.trim();
        this.retainKey = p + ":retain";
        this.cmd.ping(); // fail-fast: 验证 Redis 连接可用
    }

    @Override
    public synchronized void put(String topic, byte[] payload, int qos) {
        if (topic == null || payload == null) {
            return;
        }
        String v = qos + "\t" + Base64.getEncoder().encodeToString(payload);
        cmd.hset(retainKey, topic, v);
    }

    @Override
    public synchronized void remove(String topic) {
        if (topic == null) {
            return;
        }
        cmd.hdel(retainKey, topic);
    }

    @Override
    public synchronized List<RetainedMessage> list() {
        List<RetainedMessage> out = new ArrayList<>();
        try {
            Map<String, String> all = cmd.hgetall(retainKey);
            for (Map.Entry<String, String> e : all.entrySet()) {
                RetainedMessage m = decode(e.getKey(), e.getValue());
                if (m != null) {
                    out.add(m);
                }
            }
        } catch (RuntimeException e) {
            log.warn("RedisRetainStore list 失败", e);
        }
        return out;
    }

    private static RetainedMessage decode(String topic, String value) {
        if (topic == null || topic.isEmpty() || value == null) {
            return null;
        }
        String[] p = value.split("\t", 2);
        if (p.length != 2) {
            return null;
        }
        try {
            int qos = Integer.parseInt(p[0]);
            byte[] payload = Base64.getDecoder().decode(p[1]);
            return new RetainedMessage(topic, payload, qos);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (RuntimeException e) {
            log.debug("RedisRetainStore connection 关闭异常", e);
        }
        try {
            client.shutdown();
        } catch (RuntimeException e) {
            log.debug("RedisRetainStore client 关闭异常", e);
        }
    }
}

