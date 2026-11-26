package com.athena.tms.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("tms.outbox.relay")
record RelaySettings(
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("8") int maxAttempts,
        @DefaultValue("1s") Duration backoffBase,
        @DefaultValue("10s") Duration sendTimeout,
        @DefaultValue("60s") Duration claimLease,
        @DefaultValue("4") int workers,
        @DefaultValue("100") int batchSize,
        @DefaultValue("250ms") Duration turnBudget,
        @DefaultValue("1000") int scanWindow,
        @DefaultValue("1s") Duration lockTimeout,
        @DefaultValue("5s") Duration statementTimeout,
        @DefaultValue("60s") Duration hotelCooldownMax) {

    RelaySettings {
        if (workers < 1 || workers > 32) {
            throw new IllegalStateException("tms.outbox.relay.workers 必須介於 1 到 32，目前是 " + workers);
        }
        if (batchSize < 1 || scanWindow < 1) {
            throw new IllegalStateException("tms.outbox.relay.batch-size 與 scan-window 都必須至少為 1");
        }
    }
}
