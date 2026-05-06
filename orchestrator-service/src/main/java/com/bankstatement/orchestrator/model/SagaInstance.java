package com.bankstatement.orchestrator.model;

import com.bankstatement.common.enums.Currency;
import com.bankstatement.common.enums.SagaStatus;
import com.bankstatement.common.enums.TransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "saga_instances")
public class SagaInstance {

    @Id
    private String sagaId;

    @Enumerated(EnumType.STRING)
    private SagaStatus currentStatus;

    private String sourceAccountId;
    private String targetAccountId;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    private Currency targetCurrency;

    private BigDecimal convertedAmount;
    private BigDecimal exchangeRate;
    private String description;
    private String transactionEventId;
    private String ledgerEntryId;
    private Instant startedAt;
    private Instant completedAt;

    @OneToMany(mappedBy = "sagaInstance", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("timestamp ASC")
    private List<SagaStepEntity> steps = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (startedAt == null) startedAt = Instant.now();
        if (currentStatus == null) currentStatus = SagaStatus.STARTED;
    }

    public void addStep(String stepName, SagaStatus status, String service, String errorMessage) {
        SagaStepEntity step = new SagaStepEntity();
        step.setSagaInstance(this);
        step.setStepName(stepName);
        step.setStatus(status);
        step.setService(service);
        step.setErrorMessage(errorMessage);
        step.setTimestamp(Instant.now());
        this.steps.add(step);
    }

    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public SagaStatus getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(SagaStatus currentStatus) { this.currentStatus = currentStatus; }
    public String getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(String sourceAccountId) { this.sourceAccountId = sourceAccountId; }
    public String getTargetAccountId() { return targetAccountId; }
    public void setTargetAccountId(String targetAccountId) { this.targetAccountId = targetAccountId; }
    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }
    public Currency getTargetCurrency() { return targetCurrency; }
    public void setTargetCurrency(Currency targetCurrency) { this.targetCurrency = targetCurrency; }
    public BigDecimal getConvertedAmount() { return convertedAmount; }
    public void setConvertedAmount(BigDecimal convertedAmount) { this.convertedAmount = convertedAmount; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTransactionEventId() { return transactionEventId; }
    public void setTransactionEventId(String transactionEventId) { this.transactionEventId = transactionEventId; }
    public String getLedgerEntryId() { return ledgerEntryId; }
    public void setLedgerEntryId(String ledgerEntryId) { this.ledgerEntryId = ledgerEntryId; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public List<SagaStepEntity> getSteps() { return steps; }
    public void setSteps(List<SagaStepEntity> steps) { this.steps = steps; }
}
