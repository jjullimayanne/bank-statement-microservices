package com.bankstatement.account.controller;

import com.bankstatement.account.model.Account;
import com.bankstatement.account.repository.AccountRepository;
import com.bankstatement.account.service.AccountService;
import com.bankstatement.common.dto.AccountResponse;
import com.bankstatement.common.dto.CreateAccountRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService accountService;
    private final AccountRepository accountRepository;

    public AccountController(AccountService accountService, AccountRepository accountRepository) {
        this.accountService = accountService;
        this.accountRepository = accountRepository;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(request);
        return ResponseEntity.ok(toResponse(account));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        List<AccountResponse> accounts = accountRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountId) {
        return accountRepository.findById(accountId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private AccountResponse toResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setAccountId(account.getAccountId());
        response.setHolderName(account.getHolderName());
        response.setHolderDocument(account.getHolderDocument());
        response.setStatus(account.getStatus());
        response.setCreatedAt(account.getCreatedAt());
        response.setBalances(account.getBalances().stream()
                .map(b -> new AccountResponse.BalanceInfo(b.getCurrency(), b.getBalance()))
                .collect(Collectors.toList()));
        return response;
    }
}
