package com.bankstatement.common.events;

import com.bankstatement.common.enums.Currency;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ExchangeRateEvent {

    private String eventId;
    private String sagaId;
    private Currency sourceCurrency;
    private Currency targetCurrency;
    private BigDecimal exchangeRate;
    private BigDecimal sourceAmount;
    private BigDecimal convertedAmount;
    private boolean success;
    private String errorMessage;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    public ExchangeRateEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public Currency getSourceCurrency() { return sourceCurrency; }
    public void setSourceCurrency(Currency sourceCurrency) { this.sourceCurrency = sourceCurrency; }
    public Currency getTargetCurrency() { return targetCurrency; }
    public void setTargetCurrency(Currency targetCurrency) { this.targetCurrency = targetCurrency; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }
    public BigDecimal getSourceAmount() { return sourceAmount; }
    public void setSourceAmount(BigDecimal sourceAmount) { this.sourceAmount = sourceAmount; }
    public BigDecimal getConvertedAmount() { return convertedAmount; }
    public void setConvertedAmount(BigDecimal convertedAmount) { this.convertedAmount = convertedAmount; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
