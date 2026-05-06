package com.bankstatement.ledger.model;

import com.bankstatement.common.enums.Currency;
import com.bankstatement.common.enums.TransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    private String entryId = UUID.randomUUID().toString();

    private String sagaId;
    private String transactionEventId;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    private String debitAccountId;
    private String creditAccountId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private Currency currency;

    private String description;
    private String idempotencyKey;
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public String getTransactionEventId() { return transactionEventId; }
    public void setTransactionEventId(String transactionEventId) { this.transactionEventId = transactionEventId; }
    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
    public String getDebitAccountId() { return debitAccountId; }
    public void setDebitAccountId(String debitAccountId) { this.debitAccountId = debitAccountId; }
    public String getCreditAccountId() { return creditAccountId; }
    public void setCreditAccountId(String creditAccountId) { this.creditAccountId = creditAccountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
