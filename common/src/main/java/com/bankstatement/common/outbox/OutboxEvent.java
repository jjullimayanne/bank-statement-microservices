package com.bankstatement.common.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox Pattern entity.
 * Events are persisted to the outbox table in the SAME transaction as the business operation.
 * A scheduler reads pending events and publishes them to Kafka, ensuring no event is lost
 * even if the message broker is unavailable.
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status", columnList = "status"),
        @Index(name = "idx_outbox_created", columnList = "createdAt")
})
public class OutboxEvent {

    @Id
    private String id = UUID.randomUUID().toString();

    private String aggregateId;

    private String topic;

    @Column(name = "event_key")
    private String eventKey;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status = OutboxStatus.PENDING;

    private int retryCount = 0;

    private String errorMessage;

    private Instant createdAt;

    private Instant publishedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
