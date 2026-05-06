package com.bankstatement.common.events;

import com.bankstatement.common.enums.SagaStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public class SagaCommandEvent {

    private String eventId;
    private String sagaId;
    private SagaStatus status;
    private String targetService;
    private String command;
    private String payload;
    private String errorMessage;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    public SagaCommandEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public SagaCommandEvent(String sagaId, SagaStatus status, String targetService, String command) {
        this();
        this.sagaId = sagaId;
        this.status = status;
        this.targetService = targetService;
        this.command = command;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public SagaStatus getStatus() { return status; }
    public void setStatus(SagaStatus status) { this.status = status; }
    public String getTargetService() { return targetService; }
    public void setTargetService(String targetService) { this.targetService = targetService; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
