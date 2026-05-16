package com.mars.linker.broker.ui.rejection;

import com.mars.linker.broker.ui.monitoring.isolation.ApiRateLimiter;
import com.mars.linker.broker.ui.monitoring.model.PagedResult;
import com.mars.linker.broker.ui.rejection.model.RejectionMessage;
import com.mars.linker.broker.ui.rejection.model.RejectionReason;
import com.mars.linker.broker.ui.rejection.model.RejectionSummary;
import com.mars.linker.broker.ui.rejection.model.RejectionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ui/rejection")
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RejectionMessageController {

    private final RejectionMessageService rejectionMessageService;
    private final ApiRateLimiter apiRateLimiter;

    public RejectionMessageController(RejectionMessageService rejectionMessageService, ApiRateLimiter apiRateLimiter) {
        this.rejectionMessageService = rejectionMessageService;
        this.apiRateLimiter = apiRateLimiter;
    }

    private ResponseEntity<?> checkRateLimit() {
        if (!apiRateLimiter.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "1")
                    .body(Map.of("error", "Too many requests"));
        }
        return null;
    }

    @GetMapping("/messages")
    public ResponseEntity<?> messages(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "reason", required = false) String reason) {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        RejectionType rejectionType = type != null ? RejectionType.fromValue(type) : null;
        RejectionReason rejectionReason = reason != null ? RejectionReason.fromValue(reason) : null;
        PagedResult<RejectionMessage> result = rejectionMessageService.listMessages(page, size, rejectionType, rejectionReason);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/messages/summary")
    public ResponseEntity<?> summary() {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        RejectionSummary summary = rejectionMessageService.getSummary();
        return ResponseEntity.ok(summary);
    }

    @DeleteMapping("/messages/{id}")
    public ResponseEntity<?> removeMessage(@PathVariable long id) {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        boolean removed = rejectionMessageService.removeMessage(id);
        if (removed) {
            return ResponseEntity.ok(Map.of("id", id, "removed", true));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Rejection message not found: " + id));
    }

    @DeleteMapping("/messages")
    public ResponseEntity<?> removeMessages(@RequestParam(name = "type", required = false) String type) {
        ResponseEntity<?> limitCheck = checkRateLimit();
        if (limitCheck != null) return limitCheck;
        RejectionType rejectionType = type != null ? RejectionType.fromValue(type) : null;
        long removed = rejectionMessageService.removeMessages(rejectionType);
        return ResponseEntity.ok(Map.of("removed", removed));
    }
}
