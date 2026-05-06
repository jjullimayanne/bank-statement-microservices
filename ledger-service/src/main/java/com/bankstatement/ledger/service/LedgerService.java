package com.bankstatement.ledger.service;

import com.bankstatement.common.config.KafkaTopics;
import com.bankstatement.common.enums.TransactionType;
import com.bankstatement.common.events.LedgerConfirmedEvent;
import com.bankstatement.common.events.TransactionEvent;
import com.bankstatement.ledger.model.LedgerEntry;
import com.bankstatement.ledger.repository.LedgerEntryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerEntryRepository ledgerRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public LedgerService(LedgerEntryRepository ledgerRepository,
                         KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper) {
        this.ledgerRepository = ledgerRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.LEDGER_COMMANDS, groupId = "ledger-group")
    @Transactional
    public void handleLedgerCommand(String message) {
        try {
            TransactionEvent event = objectMapper.readValue(message, TransactionEvent.class);

            if (event.getIdempotencyKey() != null) {
                var existing = ledgerRepository.findByIdempotencyKey(event.getIdempotencyKey());
                if (existing.isPresent()) {
                    log.warn("[LEDGER] Duplicate event detected, idempotency key: {}", event.getIdempotencyKey());
                    return;
                }
            }

            LedgerEntry entry = new LedgerEntry();
            entry.setSagaId(event.getSagaId());
            entry.setTransactionEventId(event.getEventId());
            entry.setTransactionType(event.getTransactionType());
            entry.setAmount(event.getAmount());
            entry.setCurrency(event.getCurrency());
            entry.setDescription(event.getDescription());
            entry.setIdempotencyKey(event.getIdempotencyKey());

            applyDoubleEntry(entry, event);

            ledgerRepository.save(entry);

            log.info("[LEDGER] Double-entry recorded: debit={} credit={} amount={} {}",
                    entry.getDebitAccountId(), entry.getCreditAccountId(),
                    entry.getAmount(), entry.getCurrency());

            LedgerConfirmedEvent confirmed = new LedgerConfirmedEvent();
            confirmed.setSagaId(event.getSagaId());
            confirmed.setTransactionEventId(event.getEventId());
            confirmed.setDebitAccountId(entry.getDebitAccountId());
            confirmed.setCreditAccountId(entry.getCreditAccountId());
            confirmed.setAmount(entry.getAmount());
            confirmed.setCurrency(entry.getCurrency());
            confirmed.setLedgerEntryId(entry.getEntryId());

            String json = objectMapper.writeValueAsString(confirmed);
            kafkaTemplate.send(KafkaTopics.LEDGER_CONFIRMED, event.getSagaId(), json);

        } catch (JsonProcessingException e) {
            log.error("Error processing ledger command", e);
        }
    }

    private void applyDoubleEntry(LedgerEntry entry, TransactionEvent event) {
        String systemAccount = "SYSTEM_TREASURY";

        switch (event.getTransactionType()) {
            case DEPOSIT:
                entry.setDebitAccountId(systemAccount);
                entry.setCreditAccountId(event.getSourceAccountId());
                break;
            case WITHDRAWAL:
                entry.setDebitAccountId(event.getSourceAccountId());
                entry.setCreditAccountId(systemAccount);
                break;
            case TRANSFER_OUT:
                entry.setDebitAccountId(event.getSourceAccountId());
                entry.setCreditAccountId(event.getTargetAccountId() != null
                        ? event.getTargetAccountId() : systemAccount);
                break;
            case TRANSFER_IN:
                entry.setDebitAccountId(event.getSourceAccountId() != null
                        ? event.getSourceAccountId() : systemAccount);
                entry.setCreditAccountId(event.getTargetAccountId());
                break;
            case CURRENCY_EXCHANGE:
                entry.setDebitAccountId(event.getSourceAccountId());
                entry.setCreditAccountId(event.getSourceAccountId());
                break;
            case FEE:
                entry.setDebitAccountId(event.getSourceAccountId());
                entry.setCreditAccountId(systemAccount);
                break;
            case REVERSAL:
                entry.setDebitAccountId(event.getTargetAccountId() != null
                        ? event.getTargetAccountId() : systemAccount);
                entry.setCreditAccountId(event.getSourceAccountId());
                break;
            default:
                entry.setDebitAccountId(event.getSourceAccountId());
                entry.setCreditAccountId(systemAccount);
        }
    }
}
