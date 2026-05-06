package com.bankstatement.account.service;

import com.bankstatement.account.model.Account;
import com.bankstatement.account.model.AccountBalance;
import com.bankstatement.account.repository.AccountRepository;
import com.bankstatement.common.config.KafkaTopics;
import com.bankstatement.common.dto.CreateAccountRequest;
import com.bankstatement.common.enums.AccountStatus;
import com.bankstatement.common.enums.Currency;
import com.bankstatement.common.enums.TransactionType;
import com.bankstatement.common.events.AccountValidationEvent;
import com.bankstatement.common.events.TransactionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AccountService(AccountRepository accountRepository,
                          KafkaTemplate<String, String> kafkaTemplate,
                          ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Account createAccount(CreateAccountRequest request) {
        Account account = new Account();
        account.setAccountId(UUID.randomUUID().toString());
        account.setHolderName(request.getHolderName());
        account.setHolderDocument(request.getHolderDocument());
        account.setStatus(AccountStatus.ACTIVE);

        for (Currency currency : request.getCurrencies()) {
            AccountBalance balance = new AccountBalance();
            balance.setAccount(account);
            balance.setCurrency(currency);
            balance.setBalance(new BigDecimal("10000.00"));
            account.getBalances().add(balance);
        }

        return accountRepository.save(account);
    }

    @KafkaListener(topics = KafkaTopics.ACCOUNT_VALIDATION, groupId = "account-group")
    @Transactional
    public void handleAccountValidation(String message) {
        try {
            TransactionEvent event = objectMapper.readValue(message, TransactionEvent.class);
            AccountValidationEvent reply = new AccountValidationEvent();
            reply.setSagaId(event.getSagaId());
            reply.setAccountId(event.getSourceAccountId());

            Optional<Account> accountOpt = accountRepository.findById(event.getSourceAccountId());

            if (accountOpt.isEmpty()) {
                reply.setValid(false);
                reply.setErrorMessage("Account not found: " + event.getSourceAccountId());
                publishReply(reply);
                return;
            }

            Account account = accountOpt.get();

            if (account.getStatus() != AccountStatus.ACTIVE) {
                reply.setValid(false);
                reply.setErrorMessage("Account is not active: " + account.getStatus());
                publishReply(reply);
                return;
            }

            AccountBalance balance = account.getBalances().stream()
                    .filter(b -> b.getCurrency() == event.getCurrency())
                    .findFirst()
                    .orElse(null);

            if (balance == null) {
                reply.setValid(false);
                reply.setErrorMessage("Account does not have balance in currency: " + event.getCurrency());
                publishReply(reply);
                return;
            }

            boolean isDebit = event.getTransactionType() == TransactionType.WITHDRAWAL
                    || event.getTransactionType() == TransactionType.TRANSFER_OUT
                    || event.getTransactionType() == TransactionType.CURRENCY_EXCHANGE;

            if (isDebit && balance.getBalance().compareTo(event.getAmount()) < 0) {
                reply.setValid(false);
                reply.setErrorMessage("Insufficient funds. Available: " + balance.getBalance()
                        + " " + event.getCurrency() + ", Required: " + event.getAmount());
                publishReply(reply);
                return;
            }

            reply.setValid(true);
            reply.setCurrency(event.getCurrency());
            reply.setCurrentBalance(balance.getBalance());
            publishReply(reply);

            log.info("[ACCOUNT] Validated account {} for saga {}", event.getSourceAccountId(), event.getSagaId());
        } catch (JsonProcessingException e) {
            log.error("Error parsing account validation request", e);
        }
    }

    @Transactional
    public void updateBalance(String accountId, Currency currency, BigDecimal delta) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        AccountBalance balance = account.getBalances().stream()
                .filter(b -> b.getCurrency() == currency)
                .findFirst()
                .orElseGet(() -> {
                    AccountBalance newBal = new AccountBalance();
                    newBal.setAccount(account);
                    newBal.setCurrency(currency);
                    newBal.setBalance(BigDecimal.ZERO);
                    account.getBalances().add(newBal);
                    return newBal;
                });
        balance.setBalance(balance.getBalance().add(delta));
        accountRepository.save(account);
    }

    private void publishReply(AccountValidationEvent reply) {
        try {
            String json = objectMapper.writeValueAsString(reply);
            kafkaTemplate.send(KafkaTopics.ACCOUNT_VALIDATION_REPLY, reply.getSagaId(), json);
        } catch (JsonProcessingException e) {
            log.error("Error publishing account validation reply", e);
        }
    }
}
