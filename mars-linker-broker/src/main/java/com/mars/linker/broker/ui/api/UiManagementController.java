package com.mars.linker.broker.ui.api;

import com.mars.linker.broker.config.MarsLinkerMqttBrokerProperties;
import com.mars.linker.broker.netty.acl.AclProviderFactory;
import com.mars.linker.broker.netty.acl.PrefixAclProvider;
import com.mars.linker.broker.ui.collection.CollectedEvent;
import com.mars.linker.broker.ui.collection.DataCollectionService;
import com.mars.linker.broker.ui.config.MarsLinkerUiProperties;
import com.mars.linker.broker.ui.config.RuntimeConfigService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UI 管理模块 API：监控、配置、数据采集。
 */
@RestController
@RequestMapping("/api/ui")
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UiManagementController {

    private final RuntimeConfigService runtimeConfigService;
    private final DataCollectionService dataCollectionService;
    private final MarsLinkerMqttBrokerProperties brokerProperties;
    private final MarsLinkerUiProperties uiProperties;

    public UiManagementController(RuntimeConfigService runtimeConfigService,
                                  DataCollectionService dataCollectionService,
                                  MarsLinkerMqttBrokerProperties brokerProperties,
                                  MarsLinkerUiProperties uiProperties) {
        this.runtimeConfigService = runtimeConfigService;
        this.dataCollectionService = dataCollectionService;
        this.brokerProperties = brokerProperties;
        this.uiProperties = uiProperties;
    }

    @GetMapping("/config/broker")
    public Map<String, Object> brokerConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("nettyEnabled", brokerProperties.isNettyEnabled());
        config.put("tcpPort", brokerProperties.getTcpPort());
        config.put("storageEnabled", brokerProperties.isStorageEnabled());
        config.put("storageMode", brokerProperties.getStorageMode());
        config.put("sessionOfflineMaxMessages", brokerProperties.getSessionOfflineMaxMessages());
        config.put("sessionOfflineTtlMs", brokerProperties.getSessionOfflineTtlMs());
        config.put("retainMaxMessages", brokerProperties.getRetainMaxMessages());
        config.put("retainTtlMs", brokerProperties.getRetainTtlMs());
        return config;
    }

    @GetMapping("/config/runtime")
    public Map<String, Object> runtimeConfig() {
        return runtimeConfigService.snapshot();
    }

    @PutMapping("/config/runtime")
    public ResponseEntity<?> updateRuntimeConfig(@RequestBody(required = false) Map<String, Object> updates) {
        try {
            return ResponseEntity.ok(runtimeConfigService.update(updates));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/config/runtime/rollback")
    public ResponseEntity<?> rollbackRuntimeConfig() {
        try {
            return ResponseEntity.ok(runtimeConfigService.rollback());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/collection/events")
    public List<CollectedEvent> listCollectedEvents(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return dataCollectionService.listRecent(limit);
    }

    @PostMapping("/collection/events")
    public CollectedEvent collectEvent(@RequestBody CollectEventRequest request) {
        String type = request == null ? null : request.getType();
        String source = request == null ? null : request.getSource();
        String payload = request == null ? null : request.getPayload();
        return dataCollectionService.collect(type, source, payload);
    }

    @PutMapping("/collection/events/{index}/status")
    public ResponseEntity<?> updateEventStatus(@PathVariable int index,
                                                @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "status required"));
        }
        java.util.List<CollectedEvent> events = dataCollectionService.listRecent(1000);
        if (index < 0 || index >= events.size()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Event not found"));
        }
        CollectedEvent event = events.get(index);
        event.markProcessed(status);
        return ResponseEntity.ok(Map.of("index", index, "status", event.getStatus()));
    }

    @GetMapping("/module/info")
    public Map<String, Object> moduleInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("enabled", uiProperties.isEnabled());
        info.put("collectionBufferSize", uiProperties.getCollectionBufferSize());
        info.put("monitorRefreshMs", uiProperties.getMonitorRefreshMs());
        return info;
    }

    @GetMapping("/config/acl")
    public Map<String, Object> aclConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("aclEnabled", brokerProperties.isAclEnabled());
        config.put("aclMode", brokerProperties.getAclMode());
        config.put("aclDefaultDeny", brokerProperties.isAclDefaultDeny());
        config.put("allowSubscribePrefixes", brokerProperties.getAclAllowSubscribePrefixes());
        config.put("allowPublishPrefixes", brokerProperties.getAclAllowPublishPrefixes());
        config.put("denySubscribePrefixes", brokerProperties.getAclDenySubscribePrefixes());
        config.put("denyPublishPrefixes", brokerProperties.getAclDenyPublishPrefixes());
        config.put("aclHttpUrl", brokerProperties.getAclHttpUrl());
        config.put("aclHttpRefreshIntervalMs", brokerProperties.getAclHttpRefreshIntervalMs());
        return config;
    }

    @PostMapping("/config/acl/test")
    public Map<String, Object> testAcl(@RequestBody AclTestRequest request) {
        String topic = request.getTopic() == null ? "" : request.getTopic().trim();
        String action = request.getAction() == null ? "subscribe" : request.getAction().trim().toLowerCase();
        boolean allowed;
        if ("publish".equals(action)) {
            allowed = brokerProperties.isAclEnabled()
                    ? testPublishAcl(topic)
                    : true;
        } else {
            allowed = brokerProperties.isAclEnabled()
                    ? testSubscribeAcl(topic)
                    : true;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", topic);
        result.put("action", action);
        result.put("allowed", allowed);
        return result;
    }

    @PutMapping("/config/acl/prefixes")
    public Map<String, Object> updateAclPrefixes(@RequestBody Map<String, List<String>> prefixes) {
        List<String> allowSub = prefixes.get("allowSubscribePrefixes");
        List<String> allowPub = prefixes.get("allowPublishPrefixes");
        List<String> denySub = prefixes.get("denySubscribePrefixes");
        List<String> denyPub = prefixes.get("denyPublishPrefixes");
        if (allowSub != null) brokerProperties.setAclAllowSubscribePrefixes(allowSub);
        if (allowPub != null) brokerProperties.setAclAllowPublishPrefixes(allowPub);
        if (denySub != null) brokerProperties.setAclDenySubscribePrefixes(denySub);
        if (denyPub != null) brokerProperties.setAclDenyPublishPrefixes(denyPub);
        return Map.of("updated", true);
    }

    private boolean testSubscribeAcl(String topic) {
        List<String> deny = brokerProperties.getAclDenySubscribePrefixes();
        if (deny != null) {
            for (String p : deny) {
                if (p != null && !p.isEmpty() && topic.startsWith(p)) return false;
            }
        }
        List<String> allow = brokerProperties.getAclAllowSubscribePrefixes();
        if (allow == null || allow.isEmpty()) return !brokerProperties.isAclDefaultDeny();
        for (String p : allow) {
            if (p != null && !p.isEmpty() && topic.startsWith(p)) return true;
        }
        return false;
    }

    private boolean testPublishAcl(String topic) {
        List<String> deny = brokerProperties.getAclDenyPublishPrefixes();
        if (deny != null) {
            for (String p : deny) {
                if (p != null && !p.isEmpty() && topic.startsWith(p)) return false;
            }
        }
        List<String> allow = brokerProperties.getAclAllowPublishPrefixes();
        if (allow == null || allow.isEmpty()) return !brokerProperties.isAclDefaultDeny();
        for (String p : allow) {
            if (p != null && !p.isEmpty() && topic.startsWith(p)) return true;
        }
        return false;
    }

    public static class AclTestRequest {
        private String topic;
        private String action;

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }

    public static class CollectEventRequest {
        private String type;
        private String source;
        private String payload;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }
    }
}
