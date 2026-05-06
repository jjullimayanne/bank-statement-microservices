package com.bankstatement.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convenience service to save events to the outbox table.
 * Must be called within the same transaction as the business operation
 * to guarantee atomicity (event is persisted IFF the business op succeeds).
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent saveEvent(String topic, String key, Object payload, String aggregateId) {
        OutboxEvent event = new OutboxEvent();
        event.setTopic(topic);
        event.setEventKey(key);
        event.setAggregateId(aggregateId);

        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }

        outboxRepository.save(event);
        log.debug("[OUTBOX] Saved event to outbox: topic={}, key={}, aggregateId={}",
                topic, key, aggregateId);

        return event;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent saveEventRaw(String topic, String key, String jsonPayload, String aggregateId) {
        OutboxEvent event = new OutboxEvent();
        event.setTopic(topic);
        event.setEventKey(key);
        event.setAggregateId(aggregateId);
        event.setPayload(jsonPayload);

        outboxRepository.save(event);
        log.debug("[OUTBOX] Saved raw event to outbox: topic={}, key={}, aggregateId={}",
                topic, key, aggregateId);

        return event;
    }
}
