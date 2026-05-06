package com.bankstatement.ledger.config;

import com.bankstatement.ledger.repository.LedgerEntryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MetricsConfig {

    public MetricsConfig(MeterRegistry registry, LedgerEntryRepository ledgerRepository) {

        Gauge.builder("ledger_entries_total", ledgerRepository, r -> r.count())
                .tag("type", "all")
                .description("Total ledger entries")
                .register(registry);
    }
}
