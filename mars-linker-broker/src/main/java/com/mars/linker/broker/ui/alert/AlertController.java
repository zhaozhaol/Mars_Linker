package com.mars.linker.broker.ui.alert;

import com.mars.linker.broker.ui.monitoring.model.PagedResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui/alert")
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AlertController {

    private final AlertRuleRepository repository;

    public AlertController(AlertRuleRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/rules")
    public List<AlertRule> listRules() {
        return repository.listRules();
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertRule createRule(@RequestBody AlertRule rule) {
        return repository.createRule(rule);
    }

    @PutMapping("/rules/{id}")
    public AlertRule updateRule(@PathVariable String id, @RequestBody AlertRule rule) {
        return repository.updateRule(id, rule)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable String id) {
        if (!repository.deleteRule(id)) {
            throw new IllegalArgumentException("Rule not found: " + id);
        }
    }

    @PostMapping("/rules/{id}/silence")
    public Map<String, Object> silenceRule(@PathVariable String id, @RequestBody Map<String, Long> body) {
        Long untilMs = body.get("untilMs");
        if (untilMs == null || untilMs <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("untilMs must be a future timestamp");
        }
        repository.silenceRule(id, untilMs);
        return Map.of("ruleId", id, "silencedUntil", untilMs);
    }

    @GetMapping("/events")
    public List<AlertEvent> listEvents(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return repository.listEvents(activeOnly);
    }

    @GetMapping("/history")
    public PagedResult<AlertEvent> listHistory(
            @RequestParam(name = "start", required = false) Long start,
            @RequestParam(name = "end", required = false) Long end,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return repository.listHistory(start, end, page, size);
    }
}
