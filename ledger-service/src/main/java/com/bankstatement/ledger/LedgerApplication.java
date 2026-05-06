package com.bankstatement.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.bankstatement.ledger",
        "com.bankstatement.common.outbox"
})
@EntityScan(basePackages = {
        "com.bankstatement.ledger.model",
        "com.bankstatement.common.outbox"
})
@EnableJpaRepositories(basePackages = {
        "com.bankstatement.ledger.repository",
        "com.bankstatement.common.outbox"
})
@EnableScheduling
public class LedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
