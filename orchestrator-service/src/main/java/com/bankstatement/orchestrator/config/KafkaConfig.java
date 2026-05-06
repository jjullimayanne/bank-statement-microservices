package com.bankstatement.orchestrator.config;

import com.bankstatement.common.config.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic transactionEventsTopic() {
        return TopicBuilder.name(KafkaTopics.TRANSACTION_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic sagaCommandsTopic() {
        return TopicBuilder.name(KafkaTopics.SAGA_COMMANDS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic sagaRepliesTopic() {
        return TopicBuilder.name(KafkaTopics.SAGA_REPLIES).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic accountValidationTopic() {
        return TopicBuilder.name(KafkaTopics.ACCOUNT_VALIDATION).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic accountValidationReplyTopic() {
        return TopicBuilder.name(KafkaTopics.ACCOUNT_VALIDATION_REPLY).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic exchangeRateRequestTopic() {
        return TopicBuilder.name(KafkaTopics.EXCHANGE_RATE_REQUEST).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic exchangeRateReplyTopic() {
        return TopicBuilder.name(KafkaTopics.EXCHANGE_RATE_REPLY).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic ledgerCommandsTopic() {
        return TopicBuilder.name(KafkaTopics.LEDGER_COMMANDS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic ledgerConfirmedTopic() {
        return TopicBuilder.name(KafkaTopics.LEDGER_CONFIRMED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic statementUpdatesTopic() {
        return TopicBuilder.name(KafkaTopics.STATEMENT_UPDATES).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic compensationCommandsTopic() {
        return TopicBuilder.name(KafkaTopics.COMPENSATION_COMMANDS).partitions(3).replicas(1).build();
    }
}
