package com.mars.linker.broker.netty.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.mars.linker.broker.netty.protocol.TopicFilterSupport;

/**
 * 文件版 session 持久化（PoC），格式保持与历史实现一致。
 */
public final class FileSessionStore implements SessionStore {
    private static final Logger log = LoggerFactory.getLogger(FileSessionStore.class);

    private static final String SESSION_STORE_VERSION = "v1";
    private static final String SESSION_STORE_HEADER = "#session-store\t" + SESSION_STORE_VERSION;
    private static final int MAX_PERSISTED_MESSAGE_BYTES = 1024 * 1024; // 1MB
    private static final int MAX_OFFLINE_QUEUE_PER_SESSION = 10_000;

    private final Path storePath;

    public FileSessionStore(Path storePath) {
        this.storePath = storePath;
    }

    @Override
    public Map<String, SessionService.Session> loadAll() {
        if (!Files.exists(storePath)) {
            return new ConcurrentHashMap<>();
        }
        try {
            List<String> lines = Files.readAllLines(storePath, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return new ConcurrentHashMap<>();
            }
            String header = lines.get(0);
            if (!SESSION_STORE_HEADER.equals(header)) {
                log.warn("session store header mismatch, ignore load. path={} header={} expected={}",
                        storePath, header, SESSION_STORE_HEADER);
                return new ConcurrentHashMap<>();
            }
            Map<String, SessionService.Session> sessions = new ConcurrentHashMap<>();
            lines = lines.subList(1, lines.size());
            for (String line : lines) {
                String[] p = line.split("\t");
                if (p.length < 4) {
                    continue;
                }
                String kind = p[0];
                String clientId = p[1];
                SessionService.Session session = sessions.computeIfAbsent(clientId, SessionService.Session::new);
                if ("SUB".equals(kind) && p.length >= 4) {
                    try {
                        int qos = Integer.parseInt(p[3]);
                        if (qos < 0 || qos > 2) {
                            continue;
                        }
                        String filter = p[2];
                        if (!TopicFilterSupport.isValidTopicFilter(filter)
                                && !TopicFilterSupport.isShareSubscription(filter)
                                && !TopicFilterSupport.isExactTopic(filter)) {
                            continue;
                        }
                        session.subscriptionsQos.put(filter, qos);
                    } catch (NumberFormatException ignored) {
                        // skip malformed subscription line
                    }
                } else if ("MSG".equals(kind) && p.length >= 6) {
                    try {
                        String topic = p[2];
                        int qos = Integer.parseInt(p[3]);
                        if (qos < 0 || qos > 2 || !TopicFilterSupport.isExactTopic(topic)) {
                            continue;
                        }
                        boolean retain = "1".equals(p[4]);
                        byte[] payload = Base64.getDecoder().decode(p[5]);
                        if (payload.length > MAX_PERSISTED_MESSAGE_BYTES) {
                            continue;
                        }
                        if (session.offlineQueue.size() >= MAX_OFFLINE_QUEUE_PER_SESSION) {
                            continue;
                        }
                        session.offlineQueue.add(new SessionService.QueuedMessage(topic, payload, retain, qos));
                    } catch (RuntimeException ignored) {
                        // skip malformed message line
                    }
                }
            }
            return sessions;
        } catch (IOException e) {
            log.warn("会话持久化读取失败 path={}", storePath, e);
            return new ConcurrentHashMap<>();
        }
    }

    @Override
    public synchronized void persistAll(Map<String, SessionService.Session> sessions) {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            lines.add(SESSION_STORE_HEADER);
            for (SessionService.Session s : sessions.values()) {
                if (s == null) {
                    continue;
                }
                for (Map.Entry<String, Integer> sub : s.subscriptionsQos.entrySet()) {
                    lines.add("SUB\t" + s.clientId + "\t" + sub.getKey() + "\t" + (sub.getValue() == null ? 0 : sub.getValue()));
                }
                for (SessionService.QueuedMessage q : s.offlineQueue) {
                    if (q.payload == null || q.payload.length > MAX_PERSISTED_MESSAGE_BYTES) {
                        continue;
                    }
                    lines.add("MSG\t" + s.clientId + "\t" + q.topic + "\t" + q.qos + "\t" + (q.retain ? "1" : "0")
                            + "\t" + Base64.getEncoder().encodeToString(q.payload));
                }
            }
            Path tmp = storePath.resolveSibling(storePath.getFileName().toString() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.move(tmp, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("会话持久化写入失败 path={}", storePath, e);
        }
    }

    @Override
    public void deleteIfExists() {
        try {
            Files.deleteIfExists(storePath);
        } catch (IOException ignored) {
            // ignore for tests
        }
    }
}
