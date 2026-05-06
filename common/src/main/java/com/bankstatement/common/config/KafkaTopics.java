package com.bankstatement.common.config;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String TRANSACTION_EVENTS = "transaction-events";
    public static final String SAGA_COMMANDS = "saga-commands";
    public static final String SAGA_REPLIES = "saga-replies";
    public static final String ACCOUNT_VALIDATION = "account-validation";
    public static final String ACCOUNT_VALIDATION_REPLY = "account-validation-reply";
    public static final String EXCHANGE_RATE_REQUEST = "exchange-rate-request";
    public static final String EXCHANGE_RATE_REPLY = "exchange-rate-reply";
    public static final String LEDGER_COMMANDS = "ledger-commands";
    public static final String LEDGER_CONFIRMED = "ledger-confirmed";
    public static final String STATEMENT_UPDATES = "statement-updates";
    public static final String COMPENSATION_COMMANDS = "compensation-commands";
}
