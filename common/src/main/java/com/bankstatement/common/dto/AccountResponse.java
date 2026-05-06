package com.bankstatement.common.dto;

import com.bankstatement.common.enums.AccountStatus;
import com.bankstatement.common.enums.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class AccountResponse {

    private String accountId;
    private String holderName;
    private String holderDocument;
    private AccountStatus status;
    private List<BalanceInfo> balances;
    private Instant createdAt;

    public static class BalanceInfo {
        private Currency currency;
        private BigDecimal balance;

        public BalanceInfo() {}
        public BalanceInfo(Currency currency, BigDecimal balance) {
            this.currency = currency;
            this.balance = balance;
        }

        public Currency getCurrency() { return currency; }
        public void setCurrency(Currency currency) { this.currency = currency; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public String getHolderDocument() { return holderDocument; }
    public void setHolderDocument(String holderDocument) { this.holderDocument = holderDocument; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public List<BalanceInfo> getBalances() { return balances; }
    public void setBalances(List<BalanceInfo> balances) { this.balances = balances; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
