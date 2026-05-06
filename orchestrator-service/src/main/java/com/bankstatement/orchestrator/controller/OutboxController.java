package com.bankstatement.orchestrator.controller;

import com.bankstatement.common.outbox.OutboxEvent;
import com.bankstatement.common.outbox.OutboxRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outbox")
@CrossOrigin(origins = "*")
public class OutboxController {

    private final OutboxRepository outboxRepository;

    public OutboxController(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @GetMapping("/pending")
    public List<OutboxEvent> getPendingEvents() {
        return outboxRepository.findPendingEvents();
    }

    @GetMapping("/failed")
    public List<OutboxEvent> getFailedEvents() {
        return outboxRepository.findRetryableEvents();
    }

    @GetMapping("/saga/{sagaId}")
    public List<OutboxEvent> getEventsForSaga(@PathVariable String sagaId) {
        return outboxRepository.findByAggregateIdOrderByCreatedAtDesc(sagaId);
    }

    @GetMapping("/stats")
    public Map<String, Long> getStats() {
        long total = outboxRepository.count();
        long pending = outboxRepository.findPendingEvents().size();
        long failed = outboxRepository.findRetryableEvents().size();
        long published = total - pending - failed;

        return Map.of(
                "total", total,
                "pending", pending,
                "published", published,
                "failed", failed
        );
    }
}
