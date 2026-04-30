package com.mars.linker.broker.ui.monitoring;

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

    public MonitoringController(MonitoringService monitoringService,
                                MetricCategoryService metricCategoryService,
                                ApiRateLimiter apiRateLimiter,
                                MonitoringHistoryService monitoringHistoryService,
                                MonitoringSseHandler monitoringSseHandler,
                                MonitoringSelfMetricsService selfMetricsService,
                                SubscriptionDetailService subscriptionDetailService) {
        this.monitoringService = monitoringService;
        this.metricCategoryService = metricCategoryService;
        this.apiRateLimiter = apiRateLimiter;
        this.monitoringHistoryService = monitoringHistoryService;
        this.monitoringSseHandler = monitoringSseHandler;
        this.selfMetricsService = selfMetricsService;
        this.subscriptionDetailService = subscriptionDetailService;
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
}
