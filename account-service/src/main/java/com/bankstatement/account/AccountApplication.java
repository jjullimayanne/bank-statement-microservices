package com.bankstatement.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.bankstatement.account",
        "com.bankstatement.common.outbox"
})
@EntityScan(basePackages = {
        "com.bankstatement.account.model",
        "com.bankstatement.common.outbox"
})
@EnableJpaRepositories(basePackages = {
        "com.bankstatement.account.repository",
        "com.bankstatement.common.outbox"
})
@EnableScheduling
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
