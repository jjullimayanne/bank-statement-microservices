package com.bankstatement.common.dto;

import com.bankstatement.common.enums.SagaStatus;

import java.time.Instant;
import java.util.List;

public class SagaStatusResponse {

    private String sagaId;
    private SagaStatus currentStatus;
    private List<SagaStep> steps;
    private Instant startedAt;
    private Instant completedAt;

    public static class SagaStep {
        private String stepName;
        private SagaStatus status;
        private String service;
        private Instant timestamp;
        private String errorMessage;

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

    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public SagaStatus getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(SagaStatus currentStatus) { this.currentStatus = currentStatus; }
    public List<SagaStep> getSteps() { return steps; }
    public void setSteps(List<SagaStep> steps) { this.steps = steps; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
