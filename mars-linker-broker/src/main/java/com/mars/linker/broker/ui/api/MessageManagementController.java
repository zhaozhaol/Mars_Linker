package com.mars.linker.broker.ui.api;

import com.mars.linker.broker.netty.MqttProtocolHandler;
import com.mars.linker.broker.netty.store.RetainStore;
import com.mars.linker.broker.netty.store.SessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ui/messages")
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessageManagementController {

    private final MqttProtocolHandler protocolHandler;

    public MessageManagementController(MqttProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
    }

    @GetMapping("/offline")
    public ResponseEntity<?> listOfflineMessages(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        SessionService sessionService = protocolHandler.sessionService();
        List<Map<String, Object>> all = new ArrayList<>();
        for (SessionService.Session s : sessionService.allSessions()) {
            for (SessionService.QueuedMessage qm : s.offlineQueue) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("clientId", s.clientId);
                item.put("topic", qm.topic);
                item.put("qos", qm.qos);
                item.put("retain", qm.retain);
                item.put("payloadSize", qm.payload != null ? qm.payload.length : 0);
                item.put("createdAtMs", qm.createdAtMs);
                all.add(item);
            }
        }
        all.sort((a, b) -> Long.compare((Long) b.get("createdAtMs"), (Long) a.get("createdAtMs")));
        int from = (page - 1) * size;
        int to = Math.min(from + size, all.size());
        List<Map<String, Object>> pageItems = from >= all.size() ? Collections.emptyList() : all.subList(from, to);
        return ResponseEntity.ok(Map.of("items", pageItems, "totalCount", all.size()));
    }

    @DeleteMapping("/offline")
    public ResponseEntity<?> clearOfflineMessages(
            @RequestParam(name = "clientId", required = false) String clientId) {
        SessionService sessionService = protocolHandler.sessionService();
        int cleared = 0;
        if (clientId != null) {
            SessionService.Session s = sessionService.get(clientId);
            if (s != null) {
                cleared = s.offlineQueue.size();
                s.offlineQueue.clear();
                sessionService.persist(clientId);
            }
        } else {
            for (SessionService.Session s : sessionService.allSessions()) {
                cleared += s.offlineQueue.size();
                s.offlineQueue.clear();
            }
        }
        return ResponseEntity.ok(Map.of("cleared", cleared));
    }

    @GetMapping("/retain")
    public ResponseEntity<?> listRetainMessages(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        RetainStore retainStore = protocolHandler.retainStore();
        List<RetainStore.RetainedMessage> all = retainStore.list();
        int from = (page - 1) * size;
        int to = Math.min(from + size, all.size());
        List<Map<String, Object>> pageItems = new ArrayList<>();
        for (int i = from; i < to; i++) {
            RetainStore.RetainedMessage rm = all.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("topic", rm.topic);
            item.put("qos", rm.qos);
            item.put("payloadSize", rm.payload != null ? rm.payload.length : 0);
            pageItems.add(item);
        }
        return ResponseEntity.ok(Map.of("items", pageItems, "totalCount", all.size()));
    }

    @DeleteMapping("/retain")
    public ResponseEntity<?> clearRetainMessages(
            @RequestParam(name = "topic", required = false) String topic) {
        RetainStore retainStore = protocolHandler.retainStore();
        if (topic != null) {
            retainStore.remove(topic);
            return ResponseEntity.ok(Map.of("cleared", 1));
        }
        List<RetainStore.RetainedMessage> all = retainStore.list();
        int count = all.size();
        for (RetainStore.RetainedMessage rm : all) {
            retainStore.remove(rm.topic);
        }
        return ResponseEntity.ok(Map.of("cleared", count));
    }
}
