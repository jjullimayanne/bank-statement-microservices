package com.bankstatement.common.dto;

import com.bankstatement.common.enums.Currency;
import com.bankstatement.common.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class StatementResponse {

    private String accountId;
    private String holderName;
    private Instant periodStart;
    private Instant periodEnd;
    private List<StatementEntry> entries;
    private List<AccountResponse.BalanceInfo> currentBalances;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;

    public static class StatementEntry {
        private String entryId;
        private Instant timestamp;
        private TransactionType transactionType;
        private String description;
        private BigDecimal amount;
        private Currency currency;
        private BigDecimal balanceAfter;
        private String counterpartyAccountId;

        public String getEntryId() { return entryId; }
        public void setEntryId(String entryId) { this.entryId = entryId; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public TransactionType getTransactionType() { return transactionType; }
        public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public Currency getCurrency() { return currency; }
        public void setCurrency(Currency currency) { this.currency = currency; }
        public BigDecimal getBalanceAfter() { return balanceAfter; }
        public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
        public String getCounterpartyAccountId() { return counterpartyAccountId; }
        public void setCounterpartyAccountId(String counterpartyAccountId) { this.counterpartyAccountId = counterpartyAccountId; }
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant periodStart) { this.periodStart = periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }
    public List<StatementEntry> getEntries() { return entries; }
    public void setEntries(List<StatementEntry> entries) { this.entries = entries; }
    public List<AccountResponse.BalanceInfo> getCurrentBalances() { return currentBalances; }
    public void setCurrentBalances(List<AccountResponse.BalanceInfo> currentBalances) { this.currentBalances = currentBalances; }
    public BigDecimal getTotalCredits() { return totalCredits; }
    public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }
    public BigDecimal getTotalDebits() { return totalDebits; }
    public void setTotalDebits(BigDecimal totalDebits) { this.totalDebits = totalDebits; }
}
