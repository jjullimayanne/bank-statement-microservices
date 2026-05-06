package com.bankstatement.ledger.controller;

import com.bankstatement.ledger.model.LedgerEntry;
import com.bankstatement.ledger.repository.LedgerEntryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ledger")
@CrossOrigin(origins = "*")
public class LedgerController {

    private final LedgerEntryRepository ledgerRepository;

    public LedgerController(LedgerEntryRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @GetMapping("/entries")
    public ResponseEntity<List<LedgerEntry>> getAllEntries() {
        return ResponseEntity.ok(ledgerRepository.findAll());
    }

    @GetMapping("/entries/account/{accountId}")
    public ResponseEntity<List<LedgerEntry>> getEntriesByAccount(@PathVariable String accountId) {
        return ResponseEntity.ok(
                ledgerRepository.findByDebitAccountIdOrCreditAccountIdOrderByCreatedAtDesc(accountId, accountId));
    }

    @GetMapping("/entries/{entryId}")
    public ResponseEntity<LedgerEntry> getEntry(@PathVariable String entryId) {
        return ledgerRepository.findById(entryId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
