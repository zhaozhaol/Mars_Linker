package com.mars.linker.broker.ui.monitoring;

import com.mars.linker.broker.netty.MqttProtocolHandler;
import com.mars.linker.broker.netty.protocol.TopicRateLimiter;
import com.mars.linker.broker.ui.monitoring.history.MonitoringHistoryService;
import com.mars.linker.broker.ui.monitoring.isolation.ApiRateLimiter;
import com.mars.linker.broker.ui.monitoring.push.MonitoringSseHandler;
import com.mars.linker.broker.ui.monitoring.selfcheck.MonitoringSelfMetricsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/ui/monitoring")
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringController {

    private final MonitoringService monitoringService;
    private final MetricCategoryService metricCategoryService;
    private final ApiRateLimiter apiRateLimiter;
    private final MonitoringHistoryService monitoringHistoryService;
    private final MonitoringSseHandler monitoringSseHandler;
    private final MonitoringSelfMetricsService selfMetricsService;
    private final SubscriptionDetailService subscriptionDetailService;
    private final MqttProtocolHandler protocolHandler;

    public MonitoringController(MonitoringService monitoringService,
                                MetricCategoryService metricCategoryService,
                                ApiRateLimiter apiRateLimiter,
                                MonitoringHistoryService monitoringHistoryService,
                                MonitoringSseHandler monitoringSseHandler,
                                MonitoringSelfMetricsService selfMetricsService,
                                SubscriptionDetailService subscriptionDetailService,
                                MqttProtocolHandler protocolHandler) {
        this.monitoringService = monitoringService;
        this.metricCategoryService = metricCategoryService;
        this.apiRateLimiter = apiRateLimiter;
        this.monitoringHistoryService = monitoringHistoryService;
        this.monitoringSseHandler = monitoringSseHandler;
        this.selfMetricsService = selfMetricsService;
        this.subscriptionDetailService = subscriptionDetailService;
        this.protocolHandler = protocolHandler;
    }

    private ResponseEntity<?> checkRateLimit() {
        if (!apiRateLimiter.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "1")
                    .body(Map.of("error", "Too many requests"));
        }
        if (!monitoringService.isInitialized()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Monitoring service not initialized"));
        }
        return null;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview() {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        return ResponseEntity.ok(monitoringService.overview());
    }

    @GetMapping("/connections")
    public ResponseEntity<?> connections() {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        return ResponseEntity.ok(metricCategoryService.connectionMetrics());
    }

    @GetMapping("/messages")
    public ResponseEntity<?> messages() {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        return ResponseEntity.ok(metricCategoryService.messageMetrics());
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<?> subscriptions() {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        return ResponseEntity.ok(metricCategoryService.subscriptionMetrics());
    }

    @GetMapping("/system")
    public ResponseEntity<?> system() {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        return ResponseEntity.ok(metricCategoryService.systemMetrics());
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        return ResponseEntity.ok(metricCategoryService.healthStatus());
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(
            @RequestParam(name = "start", required = false) Long start,
            @RequestParam(name = "end", required = false) Long end,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        return ResponseEntity.ok(monitoringHistoryService.query(start, end, category, page, size));
    }

    @GetMapping("/sse")
    public SseEmitter sse(@RequestParam(name = "categories", defaultValue = "all") String categoriesStr) {
        Set<String> categories = new HashSet<>(Arrays.asList(categoriesStr.split(",")));
        return monitoringSseHandler.subscribe(categories);
    }

    @GetMapping("/self-metrics")
    public ResponseEntity<?> selfMetrics() {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        return ResponseEntity.ok(selfMetricsService.snapshot());
    }

    @GetMapping("/subscriptions/topics")
    public ResponseEntity<?> subscriptionTopics(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        return ResponseEntity.ok(subscriptionDetailService.listTopics(page, size));
    }

    @GetMapping("/subscriptions/subscribers")
    public ResponseEntity<?> subscriptionSubscribers(
            @RequestParam(name = "topicFilter") String topicFilter) {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        return ResponseEntity.ok(subscriptionDetailService.listSubscribers(topicFilter));
    }

    @GetMapping("/subscriptions/client")
    public ResponseEntity<?> clientSubscriptions(
            @RequestParam(name = "clientId") String clientId) {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        return ResponseEntity.ok(subscriptionDetailService.listClientSubscriptions(clientId));
    }

    @DeleteMapping("/connections/{clientId}")
    public ResponseEntity<?> disconnectClient(@PathVariable String clientId,
                                              @RequestParam(name = "reason", defaultValue = "kicked_by_admin") String reason) {
        boolean disconnected = protocolHandler.disconnectClient(clientId, reason);
        if (disconnected) {
            return ResponseEntity.ok(Map.of("clientId", clientId, "disconnected", true));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Client not found or already disconnected: " + clientId));
    }

    @PostMapping("/connections/batch-disconnect")
    public ResponseEntity<?> batchDisconnect(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        java.util.List<String> clientIds = (java.util.List<String>) body.get("clientIds");
        String reason = (String) body.getOrDefault("reason", "kicked_by_admin");
        if (clientIds == null || clientIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientIds required"));
        }
        int disconnected = 0;
        for (String cid : clientIds) {
            if (protocolHandler.disconnectClient(cid, reason)) {
                disconnected++;
            }
        }
        return ResponseEntity.ok(Map.of("requested", clientIds.size(), "disconnected", disconnected));
    }

    @GetMapping("/topic-rate-limits")
    public ResponseEntity<?> topicRateLimits() {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        TopicRateLimiter limiter = protocolHandler.getTopicRateLimiter();
        if (limiter == null) {
            return ResponseEntity.ok(Map.of("rules", 0, "stats", Map.of()));
        }
        return ResponseEntity.ok(Map.of(
                "rules", limiter.getRuleCount(),
                "stats", limiter.getStats()
        ));
    }

    @GetMapping("/topic-rate-limits/{topic}")
    public ResponseEntity<?> topicRateLimitDetail(@PathVariable String topic) {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        TopicRateLimiter limiter = protocolHandler.getTopicRateLimiter();
        if (limiter == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Topic rate limiter not initialized"));
        }
        TopicRateLimiter.TopicRateLimitStats stats = limiter.getStats(topic);
        if (stats == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No rate limit configured for topic: " + topic));
        }
        return ResponseEntity.ok(stats);
    }
}
