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
import java.util.stream.Stream;
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
        Map<String, SessionService.Session> sessions = new ConcurrentHashMap<>();
        try (Stream<String> stream = Files.lines(storePath, StandardCharsets.UTF_8)) {
            String[] headerHolder = {null};
            stream.forEach(line -> {
                if (headerHolder[0] == null) {
                    headerHolder[0] = line;
                    if (!SESSION_STORE_HEADER.equals(line)) {
                        log.warn("session store header mismatch, ignore load. path={} header={} expected={}",
                                storePath, line, SESSION_STORE_HEADER);
                        throw new StopProcessing();
                    }
                    return;
                }
                String[] p = line.split("\t");
                if (p.length < 4) {
                    return;
                }
                String kind = p[0];
                String clientId = p[1];
                SessionService.Session session = sessions.computeIfAbsent(clientId, SessionService.Session::new);
                if ("SUB".equals(kind) && p.length >= 4) {
                    try {
                        int qos = Integer.parseInt(p[3]);
                        if (qos < 0 || qos > 2) {
                            return;
                        }
                        String filter = p[2];
                        if (!TopicFilterSupport.isValidTopicFilter(filter)
                                && !TopicFilterSupport.isShareSubscription(filter)
                                && !TopicFilterSupport.isExactTopic(filter)) {
                            return;
                        }
                        session.subscriptionsQos().put(filter, qos);
                    } catch (NumberFormatException ignored) {
                    }
                } else if ("MSG".equals(kind) && p.length >= 6) {
                    try {
                        String topic = p[2];
                        int qos = Integer.parseInt(p[3]);
                        if (qos < 0 || qos > 2 || !TopicFilterSupport.isExactTopic(topic)) {
                            return;
                        }
                        boolean retain = "1".equals(p[4]);
                        long createdAtMs = p.length >= 7 ? Long.parseLong(p[5]) : System.currentTimeMillis();
                        byte[] payload = Base64.getDecoder().decode(p.length >= 7 ? p[6] : p[5]);
                        if (payload.length > MAX_PERSISTED_MESSAGE_BYTES) {
                            return;
                        }
                        if (session.offlineQueue.size() >= MAX_OFFLINE_QUEUE_PER_SESSION) {
                            return;
                        }
                        session.offlineQueue.add(new SessionService.QueuedMessage(topic, payload, retain, qos, createdAtMs));
                    } catch (RuntimeException ignored) {
                    }
                }
            });
        } catch (StopProcessing ignored) {
        } catch (IOException e) {
            log.warn("会话持久化读取失败 path={}", storePath, e);
            return new ConcurrentHashMap<>();
        }
        return sessions;
    }

    @SuppressWarnings("serial")
    private static final class StopProcessing extends RuntimeException {
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
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
                for (Map.Entry<String, Integer> sub : s.subscriptionsQos().entrySet()) {
                    lines.add("SUB\t" + s.clientId + "\t" + sub.getKey() + "\t" + (sub.getValue() == null ? 0 : sub.getValue()));
                }
                for (SessionService.QueuedMessage q : s.offlineQueue) {
                    if (q.payload == null || q.payload.length > MAX_PERSISTED_MESSAGE_BYTES) {
                        continue;
                    }
                    lines.add("MSG\t" + s.clientId + "\t" + q.topic + "\t" + q.qos + "\t" + (q.retain ? "1" : "0")
                            + "\t" + q.createdAtMs
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
