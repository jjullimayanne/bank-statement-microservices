package com.bankstatement.common.dto;

import com.bankstatement.common.enums.Currency;
import com.bankstatement.common.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class TransactionRequest {

    @NotBlank
    private String sourceAccountId;
    private String targetAccountId;
    @NotNull
    private TransactionType transactionType;
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    @NotNull
    private Currency currency;
    private Currency targetCurrency;
    private String description;

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
}
