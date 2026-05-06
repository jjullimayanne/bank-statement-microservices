package com.bankstatement.account.config;

import com.bankstatement.account.model.Account;
import com.bankstatement.account.model.AccountBalance;
import com.bankstatement.account.repository.AccountRepository;
import com.bankstatement.common.enums.AccountStatus;
import com.bankstatement.common.enums.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AccountRepository accountRepository;

    public DataSeeder(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(String... args) {
        if (accountRepository.count() > 0) {
            log.info("Accounts already seeded, skipping...");
            return;
        }

        Account joao = new Account();
        joao.setAccountId("account-joao-001");
        joao.setHolderName("João Silva");
        joao.setHolderDocument("123.456.789-00");
        joao.setStatus(AccountStatus.ACTIVE);
        addBalance(joao, Currency.BRL, new BigDecimal("25000.00"));
        addBalance(joao, Currency.USD, new BigDecimal("5000.00"));
        addBalance(joao, Currency.EUR, new BigDecimal("3000.00"));
        accountRepository.save(joao);

        Account maria = new Account();
        maria.setAccountId("account-maria-002");
        maria.setHolderName("Maria Santos");
        maria.setHolderDocument("987.654.321-00");
        maria.setStatus(AccountStatus.ACTIVE);
        addBalance(maria, Currency.BRL, new BigDecimal("15000.00"));
        addBalance(maria, Currency.EUR, new BigDecimal("8000.00"));
        addBalance(maria, Currency.GBP, new BigDecimal("2000.00"));
        accountRepository.save(maria);

        log.info("Seeded 2 mock accounts: João (BRL/USD/EUR) and Maria (BRL/EUR/GBP)");
    }

    private void addBalance(Account account, Currency currency, BigDecimal amount) {
        AccountBalance balance = new AccountBalance();
        balance.setAccount(account);
        balance.setCurrency(currency);
        balance.setBalance(amount);
        account.getBalances().add(balance);
    }
}
