package com.bankstatement.account.model;

import com.bankstatement.common.enums.AccountStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private String accountId;

    private String holderName;
    private String holderDocument;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<AccountBalance> balances = new ArrayList<>();

    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = AccountStatus.ACTIVE;
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public String getHolderDocument() { return holderDocument; }
    public void setHolderDocument(String holderDocument) { this.holderDocument = holderDocument; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public List<AccountBalance> getBalances() { return balances; }
    public void setBalances(List<AccountBalance> balances) { this.balances = balances; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
