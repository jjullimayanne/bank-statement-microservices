package com.bankstatement.ledger.repository;

import com.bankstatement.ledger.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {

    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    List<LedgerEntry> findByDebitAccountIdOrCreditAccountIdOrderByCreatedAtDesc(
            String debitAccountId, String creditAccountId);
}
