package com.mars.linker.broker.ui.monitoring.push;

import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import com.mars.linker.broker.ui.monitoring.isolation.MonitoringFaultBoundary;
import com.mars.linker.broker.ui.monitoring.isolation.MonitoringResourceBudget;
import com.mars.linker.broker.ui.monitoring.model.PushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE 监控推送处理器，支持超时保护和心跳保活。
 * <p>
 * 设计决策：
 * - SSE 连接超时默认 30 分钟，防止资源泄漏
 * - 心跳保活通过 SSE 注释帧 `: keepalive` 实现
 * - 超时时间可通过 mars.linker.ui.sse-timeout-minutes 配置
 * </p>
 */
@Component
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringSseHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitoringSseHandler.class);
    private static final int DEFAULT_TIMEOUT_MINUTES = 30;

    private final CopyOnWriteArrayList<SseConnection> connections = new CopyOnWriteArrayList<>();
    private final MonitoringResourceBudget resourceBudget;
    private final MarsLinkerUiProperties uiProps;
    private final Executor pushExecutor;
    private final AtomicLong eventIdSeq = new AtomicLong(1);
    private final ScheduledExecutorService keepaliveScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "broker-sse-keepalive");
                t.setDaemon(true);
                return t;
            });

    public MonitoringSseHandler(MonitoringResourceBudget resourceBudget,
                                MarsLinkerUiProperties uiProps,
                                @Qualifier("monitoring-push-executor") Executor pushExecutor) {
        this.resourceBudget = resourceBudget;
        this.uiProps = uiProps;
        this.pushExecutor = pushExecutor;
    }

    public SseEmitter subscribe(Set<String> categories) {
        if (!resourceBudget.checkPushConnectionLimit(connections.size())) {
            throw new RuntimeException("Push connection limit reached");
        }

        long timeoutMs = resolveTimeoutMs();
        SseEmitter emitter = new SseEmitter(timeoutMs);
        SseConnection conn = new SseConnection(emitter, categories);
        connections.add(conn);

        int keepaliveIntervalSec = uiProps.getSseKeepaliveIntervalSeconds();
        if (keepaliveIntervalSec > 0) {
            ScheduledFuture<?> keepaliveFuture = keepaliveScheduler.scheduleAtFixedRate(() -> {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException e) {
                    connections.remove(conn);
                }
            }, keepaliveIntervalSec, keepaliveIntervalSec, java.util.concurrent.TimeUnit.SECONDS);
            conn.keepaliveFuture = keepaliveFuture;
        }

        emitter.onCompletion(() -> {
            connections.remove(conn);
            cancelKeepalive(conn);
        });
        emitter.onTimeout(() -> {
            connections.remove(conn);
            cancelKeepalive(conn);
        });
        emitter.onError(e -> {
            connections.remove(conn);
            cancelKeepalive(conn);
        });

        return emitter;
    }

    public void push(PushMessage message) {
        MonitoringFaultBoundary.execute(() -> {
            for (SseConnection conn : connections) {
                if (conn.categories.contains(message.getCategory()) || conn.categories.contains("all")) {
                    pushExecutor.execute(() -> {
                        try {
                            conn.emitter.send(SseEmitter.event()
                                    .id(String.valueOf(eventIdSeq.getAndIncrement()))
                                    .name(message.getType())
                                    .data(message));
                        } catch (IOException e) {
                            connections.remove(conn);
                            cancelKeepalive(conn);
                        }
                    });
                }
            }
        });
    }

    public int getConnectionCount() {
        return connections.size();
    }

    private long resolveTimeoutMs() {
        int timeoutMin = uiProps.getSseTimeoutMinutes();
        if (timeoutMin <= 0) {
            log.warn("SSE 超时配置为 {}（非正数），使用默认值 {} 分钟", timeoutMin, DEFAULT_TIMEOUT_MINUTES);
            timeoutMin = DEFAULT_TIMEOUT_MINUTES;
        }
        return (long) timeoutMin * 60 * 1000;
    }

    private void cancelKeepalive(SseConnection conn) {
        if (conn.keepaliveFuture != null) {
            conn.keepaliveFuture.cancel(false);
            conn.keepaliveFuture = null;
        }
    }

    private static class SseConnection {
        final SseEmitter emitter;
        final Set<String> categories;
        volatile ScheduledFuture<?> keepaliveFuture;

        SseConnection(SseEmitter emitter, Set<String> categories) {
            this.emitter = emitter;
            this.categories = categories;
        }
    }
}
