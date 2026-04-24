package com.mars.linker.broker.netty;

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

/**
 * 最小可用的 Retain 持久化实现（文件存储）。
 */
public class FileRetainStore implements RetainStore {
    private static final Logger log = LoggerFactory.getLogger(FileRetainStore.class);
    private static final String RETAIN_STORE_VERSION = "v1";
    private static final String RETAIN_STORE_HEADER = "#retain-store\t" + RETAIN_STORE_VERSION;
    private static final int MAX_RETAINED_PAYLOAD_BYTES = 1024 * 1024; // 1MB

    private final Path storePath;
    private final ConcurrentHashMap<String, RetainedMessage> retained = new ConcurrentHashMap<>();

    public FileRetainStore(Path storePath) {
        this.storePath = storePath;
        loadFromDisk();
    }

    @Override
    public synchronized void put(String topic, byte[] payload, int qos) {
        byte[] copy = new byte[payload.length];
        System.arraycopy(payload, 0, copy, 0, payload.length);
        retained.put(topic, new RetainedMessage(topic, copy, qos));
        persist();
    }

    @Override
    public synchronized void remove(String topic) {
        retained.remove(topic);
        persist();
    }

    @Override
    public List<RetainedMessage> list() {
        return new ArrayList<>(retained.values());
    }

    private void loadFromDisk() {
        if (!Files.exists(storePath)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(storePath, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return;
            }
            String header = lines.get(0);
            if (!RETAIN_STORE_HEADER.equals(header)) {
                log.warn("retain store header mismatch, ignore load. path={} header={} expected={}",
                        storePath, header, RETAIN_STORE_HEADER);
                return;
            }
            lines = lines.subList(1, lines.size());
            for (String line : lines) {
                String[] parts = line.split("\t", 3);
                if (parts.length != 3) {
                    continue;
                }
                String topic = parts[0];
                if (topic == null || topic.isEmpty()) {
                    continue;
                }
                int qos;
                try {
                    qos = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (qos < 0 || qos > 2) {
                    continue;
                }
                byte[] payload;
                try {
                    payload = Base64.getDecoder().decode(parts[2]);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (payload.length > MAX_RETAINED_PAYLOAD_BYTES) {
                    log.warn("skip oversized retained payload topic={} bytes={} max={}",
                            topic, payload.length, MAX_RETAINED_PAYLOAD_BYTES);
                    continue;
                }
                retained.put(topic, new RetainedMessage(topic, payload, qos));
            }
        } catch (IOException e) {
            // 最小 PoC：读失败时保持内存空，不中断 Broker 启动。
            log.warn("failed to load retain store path={}", storePath, e);
        }
    }

    private void persist() {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            lines.add(RETAIN_STORE_HEADER);
            for (Map.Entry<String, RetainedMessage> e : retained.entrySet()) {
                RetainedMessage m = e.getValue();
                lines.add(m.topic + "\t" + m.qos + "\t" + Base64.getEncoder().encodeToString(m.payload));
            }
            Path tmp = storePath.resolveSibling(storePath.getFileName().toString() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.move(tmp, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // 最小 PoC：持久化失败不影响主流程，后续可接入可观测告警。
            log.warn("failed to persist retain store path={}", storePath, e);
        }
    }
}
