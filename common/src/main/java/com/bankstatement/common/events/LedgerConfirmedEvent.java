package com.bankstatement.common.events;

import com.bankstatement.common.enums.Currency;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class LedgerConfirmedEvent {

    private String eventId;
    private String sagaId;
    private String transactionEventId;
    private String debitAccountId;
    private String creditAccountId;
    private BigDecimal amount;
    private Currency currency;
    private BigDecimal debitAccountBalance;
    private BigDecimal creditAccountBalance;
    private String ledgerEntryId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    public LedgerConfirmedEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public String getTransactionEventId() { return transactionEventId; }
    public void setTransactionEventId(String transactionEventId) { this.transactionEventId = transactionEventId; }
    public String getDebitAccountId() { return debitAccountId; }
    public void setDebitAccountId(String debitAccountId) { this.debitAccountId = debitAccountId; }
    public String getCreditAccountId() { return creditAccountId; }
    public void setCreditAccountId(String creditAccountId) { this.creditAccountId = creditAccountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }
    public BigDecimal getDebitAccountBalance() { return debitAccountBalance; }
    public void setDebitAccountBalance(BigDecimal debitAccountBalance) { this.debitAccountBalance = debitAccountBalance; }
    public BigDecimal getCreditAccountBalance() { return creditAccountBalance; }
    public void setCreditAccountBalance(BigDecimal creditAccountBalance) { this.creditAccountBalance = creditAccountBalance; }
    public String getLedgerEntryId() { return ledgerEntryId; }
    public void setLedgerEntryId(String ledgerEntryId) { this.ledgerEntryId = ledgerEntryId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
