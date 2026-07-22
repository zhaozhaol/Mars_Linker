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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        long fileMtime;
        try {
            fileMtime = Files.getLastModifiedTime(storePath).toMillis();
        } catch (IOException e) {
            fileMtime = System.currentTimeMillis();
        }
        final boolean[] sawSess = {false};
        final long fallbackMtime = fileMtime;
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
                if (p.length < 1) {
                    return;
                }
                String kind = p[0];
                if ("SESS".equals(kind) && p.length >= 3) {
                    String clientId = p[1];
                    SessionService.Session session = sessions.computeIfAbsent(clientId, SessionService.Session::new);
                    try {
                        long lastActivityMs = Long.parseLong(p[2]);
                        session.touchActivity(lastActivityMs);
                        sawSess[0] = true;
                    } catch (NumberFormatException ignored) {
                    }
                } else if ("SUB".equals(kind) && p.length >= 4) {
                    String clientId = p[1];
                    SessionService.Session session = sessions.computeIfAbsent(clientId, SessionService.Session::new);
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
                    String clientId = p[1];
                    SessionService.Session session = sessions.computeIfAbsent(clientId, SessionService.Session::new);
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
        // 旧格式兼容：持久化文件无 SESS 行时，用文件最后修改时间作为 lastActivityMs
        if (!sawSess[0] && !sessions.isEmpty()) {
            for (SessionService.Session s : sessions.values()) {
                s.touchActivity(fallbackMtime);
            }
            log.info("检测到旧格式持久化文件（无 SESS 行），{} 个会话 lastActivityMs 回退为文件修改时间",
                    sessions.size());
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
                appendSessionLines(lines, s);
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
    public boolean supportsIncrementalPersist() {
        return true;
    }

    /**
     * 批量增量持久化：单次读-改-写完成所有 dirty/removed 客户端的更新。
     * 相比全量 persistAll，避免了序列化全部内存会话的开销。
     */
    @Override
    public synchronized void persistIncremental(
            Map<String, SessionService.Session> dirtySessions,
            Set<String> removedClientIds,
            Map<String, SessionService.Session> allSessions) {
        if (dirtySessions.isEmpty() && removedClientIds.isEmpty()) {
            return;
        }
        Set<String> affectedClientIds = new HashSet<>(removedClientIds);
        affectedClientIds.addAll(dirtySessions.keySet());

        try {
            List<String> lines = new ArrayList<>();
            boolean headerFound = false;
            if (Files.exists(storePath)) {
                for (String line : Files.readAllLines(storePath, StandardCharsets.UTF_8)) {
                    if (line.startsWith("#")) {
                        if (!headerFound && SESSION_STORE_HEADER.equals(line)) {
                            headerFound = true;
                        }
                        lines.add(line);
                        continue;
                    }
                    String[] p = line.split("\t", 3);
                    if (p.length >= 2 && affectedClientIds.contains(p[1])) {
                        continue; // skip old lines for affected clients
                    }
                    lines.add(line);
                }
            }
            if (!headerFound) {
                lines.add(0, SESSION_STORE_HEADER);
                Path parent = storePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            }
            for (SessionService.Session s : dirtySessions.values()) {
                if (s == null) {
                    continue;
                }
                appendSessionLines(lines, s);
            }
            Path tmp = storePath.resolveSibling(storePath.getFileName().toString() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.move(tmp, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("增量持久化失败 path={} dirty={} removed={}", storePath,
                    dirtySessions.size(), removedClientIds.size(), e);
        }
    }

    private void appendSessionLines(List<String> lines, SessionService.Session s) {
        lines.add("SESS\t" + s.clientId + "\t" + s.lastActivityMs());
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

    @Override
    public void deleteIfExists() {
        try {
            Files.deleteIfExists(storePath);
        } catch (IOException ignored) {
            // ignore for tests
        }
    }
}
