package com.mars.linker.broker.ui.monitoring.push;

import com.mars.linker.broker.ui.monitoring.isolation.MonitoringFaultBoundary;
import com.mars.linker.broker.ui.monitoring.isolation.MonitoringResourceBudget;
import com.mars.linker.broker.ui.monitoring.model.PushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringSseHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitoringSseHandler.class);

    private final CopyOnWriteArrayList<SseConnection> connections = new CopyOnWriteArrayList<>();
    private final MonitoringResourceBudget resourceBudget;
    private final AtomicLong eventIdSeq = new AtomicLong(1);

    public MonitoringSseHandler(MonitoringResourceBudget resourceBudget) {
        this.resourceBudget = resourceBudget;
    }

    public SseEmitter subscribe(Set<String> categories) {
        if (!resourceBudget.checkPushConnectionLimit(connections.size())) {
            throw new RuntimeException("Push connection limit reached");
        }
        SseEmitter emitter = new SseEmitter(0L);
        SseConnection conn = new SseConnection(emitter, categories);
        connections.add(conn);

        emitter.onCompletion(() -> connections.remove(conn));
        emitter.onTimeout(() -> connections.remove(conn));
        emitter.onError(e -> connections.remove(conn));

        return emitter;
    }

    public void push(PushMessage message) {
        MonitoringFaultBoundary.execute(() -> {
            for (SseConnection conn : connections) {
                if (conn.categories.contains(message.getCategory()) || conn.categories.contains("all")) {
                    try {
                        conn.emitter.send(SseEmitter.event()
                                .id(String.valueOf(eventIdSeq.getAndIncrement()))
                                .name(message.getType())
                                .data(message));
                    } catch (IOException e) {
                        connections.remove(conn);
                    }
                }
            }
        });
    }

    public int getConnectionCount() {
        return connections.size();
    }

    private static class SseConnection {
        final SseEmitter emitter;
        final Set<String> categories;
        SseConnection(SseEmitter emitter, Set<String> categories) {
            this.emitter = emitter;
            this.categories = categories;
        }
    }
}
