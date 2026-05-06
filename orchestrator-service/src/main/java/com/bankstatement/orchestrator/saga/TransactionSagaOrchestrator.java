package com.bankstatement.orchestrator.saga;

import com.bankstatement.common.config.KafkaTopics;
import com.bankstatement.common.dto.TransactionRequest;
import com.bankstatement.common.enums.SagaStatus;
import com.bankstatement.common.enums.TransactionType;
import com.bankstatement.common.events.*;
import com.bankstatement.orchestrator.model.SagaInstance;
import com.bankstatement.orchestrator.repository.SagaInstanceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TransactionSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TransactionSagaOrchestrator.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SagaInstanceRepository sagaRepository;
    private final ObjectMapper objectMapper;

    public TransactionSagaOrchestrator(KafkaTemplate<String, String> kafkaTemplate,
                                       SagaInstanceRepository sagaRepository,
                                       ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.sagaRepository = sagaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SagaInstance startSaga(TransactionRequest request) {
        String sagaId = UUID.randomUUID().toString();

        SagaInstance saga = new SagaInstance();
        saga.setSagaId(sagaId);
        saga.setCurrentStatus(SagaStatus.STARTED);
        saga.setSourceAccountId(request.getSourceAccountId());
        saga.setTargetAccountId(request.getTargetAccountId());
        saga.setTransactionType(request.getTransactionType());
        saga.setAmount(request.getAmount());
        saga.setCurrency(request.getCurrency());
        saga.setTargetCurrency(request.getTargetCurrency());
        saga.setDescription(request.getDescription());
        saga.addStep("SAGA_STARTED", SagaStatus.STARTED, "orchestrator", null);

        sagaRepository.save(saga);

        log.info("[SAGA-{}] Started for {} {} {} -> {}",
                sagaId, request.getTransactionType(), request.getAmount(),
                request.getCurrency(), request.getSourceAccountId());

        sendAccountValidation(saga);
        return saga;
    }

    private void sendAccountValidation(SagaInstance saga) {
        TransactionEvent event = new TransactionEvent();
        event.setSagaId(saga.getSagaId());
        event.setSourceAccountId(saga.getSourceAccountId());
        event.setTargetAccountId(saga.getTargetAccountId());
        event.setTransactionType(saga.getTransactionType());
        event.setAmount(saga.getAmount());
        event.setCurrency(saga.getCurrency());

        publishEvent(KafkaTopics.ACCOUNT_VALIDATION, saga.getSagaId(), event);
        log.info("[SAGA-{}] Sent account validation request", saga.getSagaId());
    }

    @KafkaListener(topics = KafkaTopics.ACCOUNT_VALIDATION_REPLY, groupId = "orchestrator-group")
    @Transactional
    public void handleAccountValidationReply(String message) {
        try {
            AccountValidationEvent event = objectMapper.readValue(message, AccountValidationEvent.class);
            SagaInstance saga = sagaRepository.findById(event.getSagaId()).orElse(null);
            if (saga == null) return;

            if (event.isValid()) {
                saga.setCurrentStatus(SagaStatus.ACCOUNT_VALIDATED);
                saga.addStep("ACCOUNT_VALIDATED", SagaStatus.ACCOUNT_VALIDATED, "account-service", null);
                sagaRepository.save(saga);

                log.info("[SAGA-{}] Account validated, checking currency exchange", saga.getSagaId());

                boolean needsExchange = saga.getTargetCurrency() != null
                        && saga.getCurrency() != saga.getTargetCurrency();

                if (needsExchange) {
                    sendExchangeRateRequest(saga);
                } else {
                    sendLedgerCommand(saga);
                }
            } else {
                saga.setCurrentStatus(SagaStatus.ACCOUNT_VALIDATION_FAILED);
                saga.addStep("ACCOUNT_VALIDATION_FAILED", SagaStatus.ACCOUNT_VALIDATION_FAILED,
                        "account-service", event.getErrorMessage());
                saga.setCompletedAt(Instant.now());
                sagaRepository.save(saga);
                log.warn("[SAGA-{}] Account validation failed: {}", saga.getSagaId(), event.getErrorMessage());
            }
        } catch (JsonProcessingException e) {
            log.error("Error parsing account validation reply", e);
        }
    }

    private void sendExchangeRateRequest(SagaInstance saga) {
        ExchangeRateEvent event = new ExchangeRateEvent();
        event.setSagaId(saga.getSagaId());
        event.setSourceCurrency(saga.getCurrency());
        event.setTargetCurrency(saga.getTargetCurrency());
        event.setSourceAmount(saga.getAmount());

        publishEvent(KafkaTopics.EXCHANGE_RATE_REQUEST, saga.getSagaId(), event);
        log.info("[SAGA-{}] Sent exchange rate request {} -> {}",
                saga.getSagaId(), saga.getCurrency(), saga.getTargetCurrency());
    }

    @KafkaListener(topics = KafkaTopics.EXCHANGE_RATE_REPLY, groupId = "orchestrator-group")
    @Transactional
    public void handleExchangeRateReply(String message) {
        try {
            ExchangeRateEvent event = objectMapper.readValue(message, ExchangeRateEvent.class);
            SagaInstance saga = sagaRepository.findById(event.getSagaId()).orElse(null);
            if (saga == null) return;

            if (event.isSuccess()) {
                saga.setCurrentStatus(SagaStatus.CURRENCY_CONVERTED);
                saga.setExchangeRate(event.getExchangeRate());
                saga.setConvertedAmount(event.getConvertedAmount());
                saga.addStep("CURRENCY_CONVERTED", SagaStatus.CURRENCY_CONVERTED, "exchange-service", null);
                sagaRepository.save(saga);

                log.info("[SAGA-{}] Currency converted: {} {} = {} {}",
                        saga.getSagaId(), event.getSourceAmount(), event.getSourceCurrency(),
                        event.getConvertedAmount(), event.getTargetCurrency());

                sendLedgerCommand(saga);
            } else {
                saga.setCurrentStatus(SagaStatus.CURRENCY_CONVERSION_FAILED);
                saga.addStep("CURRENCY_CONVERSION_FAILED", SagaStatus.CURRENCY_CONVERSION_FAILED,
                        "exchange-service", event.getErrorMessage());
                saga.setCompletedAt(Instant.now());
                sagaRepository.save(saga);

                startCompensation(saga, "Currency conversion failed");
            }
        } catch (JsonProcessingException e) {
            log.error("Error parsing exchange rate reply", e);
        }
    }

    private void sendLedgerCommand(SagaInstance saga) {
        TransactionEvent event = new TransactionEvent();
        event.setSagaId(saga.getSagaId());
        event.setSourceAccountId(saga.getSourceAccountId());
        event.setTargetAccountId(saga.getTargetAccountId());
        event.setTransactionType(saga.getTransactionType());
        event.setAmount(saga.getConvertedAmount() != null ? saga.getConvertedAmount() : saga.getAmount());
        event.setCurrency(saga.getTargetCurrency() != null ? saga.getTargetCurrency() : saga.getCurrency());
        event.setDescription(saga.getDescription());

        publishEvent(KafkaTopics.LEDGER_COMMANDS, saga.getSagaId(), event);
        log.info("[SAGA-{}] Sent ledger recording command", saga.getSagaId());
    }

    @KafkaListener(topics = KafkaTopics.LEDGER_CONFIRMED, groupId = "orchestrator-group")
    @Transactional
    public void handleLedgerConfirmed(String message) {
        try {
            LedgerConfirmedEvent event = objectMapper.readValue(message, LedgerConfirmedEvent.class);
            SagaInstance saga = sagaRepository.findById(event.getSagaId()).orElse(null);
            if (saga == null) return;

            saga.setCurrentStatus(SagaStatus.LEDGER_RECORDED);
            saga.setLedgerEntryId(event.getLedgerEntryId());
            saga.addStep("LEDGER_RECORDED", SagaStatus.LEDGER_RECORDED, "ledger-service", null);
            sagaRepository.save(saga);

            log.info("[SAGA-{}] Ledger entry recorded: {}", saga.getSagaId(), event.getLedgerEntryId());

            publishEvent(KafkaTopics.STATEMENT_UPDATES, saga.getSagaId(), event);

            saga.setCurrentStatus(SagaStatus.COMPLETED);
            saga.addStep("SAGA_COMPLETED", SagaStatus.COMPLETED, "orchestrator", null);
            saga.setCompletedAt(Instant.now());
            sagaRepository.save(saga);

            log.info("[SAGA-{}] Saga completed successfully", saga.getSagaId());
        } catch (JsonProcessingException e) {
            log.error("Error parsing ledger confirmed event", e);
        }
    }

    private void startCompensation(SagaInstance saga, String reason) {
        saga.setCurrentStatus(SagaStatus.COMPENSATING);
        saga.addStep("COMPENSATION_STARTED", SagaStatus.COMPENSATING, "orchestrator", reason);
        sagaRepository.save(saga);

        SagaCommandEvent compensation = new SagaCommandEvent(
                saga.getSagaId(), SagaStatus.COMPENSATING, "account-service", "ROLLBACK_RESERVATION");
        publishEvent(KafkaTopics.COMPENSATION_COMMANDS, saga.getSagaId(), compensation);

        log.info("[SAGA-{}] Compensation started: {}", saga.getSagaId(), reason);
    }

    private <T> void publishEvent(String topic, String key, T event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, json);
        } catch (JsonProcessingException e) {
            log.error("Error serializing event for topic {}", topic, e);
        }
    }
}
