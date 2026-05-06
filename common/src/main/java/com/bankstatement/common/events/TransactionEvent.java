package com.bankstatement.common.events;

import com.bankstatement.common.enums.Currency;
import com.bankstatement.common.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TransactionEvent {

    private String eventId;
    private String sagaId;
    private String sourceAccountId;
    private String targetAccountId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private Currency currency;
    private Currency targetCurrency;
    private String description;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
    private String idempotencyKey;

    public TransactionEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public TransactionEvent(String sourceAccountId, TransactionType transactionType,
                            BigDecimal amount, Currency currency, String description) {
        this();
        this.sourceAccountId = sourceAccountId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.idempotencyKey = UUID.randomUUID().toString();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
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
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
