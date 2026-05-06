package com.bankstatement.orchestrator.model;

import com.bankstatement.common.enums.SagaStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_steps")
public class SagaStepEntity {

    @Id
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_id")
    private SagaInstance sagaInstance;

    private String stepName;

    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    private String service;
    private Instant timestamp;

    @Column(length = 1000)
    private String errorMessage;

    @PrePersist
    public void prePersist() {
        if (timestamp == null) timestamp = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public SagaInstance getSagaInstance() { return sagaInstance; }
    public void setSagaInstance(SagaInstance sagaInstance) { this.sagaInstance = sagaInstance; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public SagaStatus getStatus() { return status; }
    public void setStatus(SagaStatus status) { this.status = status; }
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
