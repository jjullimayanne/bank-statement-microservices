package com.bankstatement.orchestrator.config;

import com.bankstatement.orchestrator.repository.SagaInstanceRepository;
import com.bankstatement.common.outbox.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MetricsConfig {

    public MetricsConfig(MeterRegistry registry,
                         SagaInstanceRepository sagaRepository,
                         OutboxRepository outboxRepository) {

        Gauge.builder("saga_total", sagaRepository, r -> r.count())
                .tag("status", "all")
                .description("Total saga instances")
                .register(registry);

        Gauge.builder("outbox_events_pending", outboxRepository,
                r -> r.findPendingEvents().size())
                .description("Pending outbox events")
                .register(registry);

        Gauge.builder("outbox_events_total", outboxRepository, r -> r.count())
                .tag("status", "all")
                .description("Total outbox events")
                .register(registry);
    }
}
