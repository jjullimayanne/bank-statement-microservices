package com.bankstatement.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.bankstatement.orchestrator",
        "com.bankstatement.common.outbox"
})
@EntityScan(basePackages = {
        "com.bankstatement.orchestrator.model",
        "com.bankstatement.common.outbox"
})
@EnableJpaRepositories(basePackages = {
        "com.bankstatement.orchestrator.repository",
        "com.bankstatement.common.outbox"
})
@EnableScheduling
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
