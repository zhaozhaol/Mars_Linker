package com.mars.linker.broker.netty;

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
final class RedisRetainStore implements RetainStore {
    private static final Logger log = LoggerFactory.getLogger(RedisRetainStore.class);

    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> cmd;
    private final String retainKey;

    RedisRetainStore(RedisURI redisUri, String keyPrefix) {
        RedisClient client = RedisClient.create(redisUri);
        this.connection = client.connect();
        this.cmd = connection.sync();
        String p = keyPrefix == null || keyPrefix.trim().isEmpty() ? "ml" : keyPrefix.trim();
        this.retainKey = p + ":retain";
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
}

