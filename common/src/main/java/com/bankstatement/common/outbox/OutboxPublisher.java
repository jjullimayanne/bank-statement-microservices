package com.bankstatement.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox Scheduler / Relay.
 * Periodically polls the outbox table for PENDING events and publishes them to Kafka.
 * If Kafka is unavailable, events remain in the database and are retried on the next cycle.
 * This guarantees at-least-once delivery even during broker outages.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int MAX_RETRIES = 5;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findPendingEvents();
        if (!pending.isEmpty()) {
            log.debug("[OUTBOX] Found {} pending events to publish", pending.size());
        }

        for (OutboxEvent event : pending) {
            tryPublish(event);
        }
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void retryFailedEvents() {
        List<OutboxEvent> retryable = outboxRepository.findRetryableEvents();
        if (!retryable.isEmpty()) {
            log.info("[OUTBOX] Retrying {} failed events", retryable.size());
        }

        for (OutboxEvent event : retryable) {
            tryPublish(event);
        }
    }

    private void tryPublish(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get();

            event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            outboxRepository.save(event);

            log.info("[OUTBOX] Published event {} to topic {} (key={})",
                    event.getId(), event.getTopic(), event.getEventKey());

        } catch (Exception e) {
            event.setRetryCount(event.getRetryCount() + 1);
            event.setErrorMessage(e.getMessage());

            if (event.getRetryCount() >= MAX_RETRIES) {
                event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                log.error("[OUTBOX] Event {} permanently failed after {} retries: {}",
                        event.getId(), MAX_RETRIES, e.getMessage());
            } else {
                log.warn("[OUTBOX] Event {} publish failed (retry {}/{}): {}",
                        event.getId(), event.getRetryCount(), MAX_RETRIES, e.getMessage());
            }

            outboxRepository.save(event);
        }
    }
}
