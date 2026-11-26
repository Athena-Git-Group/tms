package com.athena.tms.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TenantMetricsConfigTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void hotelsBeyondTheCapAreNotRecordedWhileKnownHotelsKeepRecording() {
        registry.config().meterFilter(new TenantMetricsConfig().tenantTagCap(2));

        for (long hotel = 1; hotel <= 3; hotel++) {
            registry.counter("tms.outbox.relay.sent", TenantMetricsConfig.TENANT_TAG, Long.toString(hotel)).increment();
        }
        registry.counter("tms.outbox.relay.sent", TenantMetricsConfig.TENANT_TAG, "1").increment();
        registry.timer("tms.outbox.relay.lag", TenantMetricsConfig.TENANT_TAG, "2").record(java.time.Duration.ofMillis(5));

        assertThat(registry.find("tms.outbox.relay.sent").counters()).hasSize(2);
        assertThat(registry.find("tms.outbox.relay.sent").tag(TenantMetricsConfig.TENANT_TAG, "3").counter()).isNull();
        assertThat(registry.get("tms.outbox.relay.sent").tag(TenantMetricsConfig.TENANT_TAG, "1").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("tms.outbox.relay.lag").tag(TenantMetricsConfig.TENANT_TAG, "2").timer().count()).isEqualTo(1);
    }

    @Test
    void metersWithoutTheTenantTagAreNeverCapped() {
        registry.config().meterFilter(new TenantMetricsConfig().tenantTagCap(1));
        registry.counter("tms.outbox.relay.sent", TenantMetricsConfig.TENANT_TAG, "1").increment();

        registry.counter("tms.outbox.relay.passes").increment();
        registry.counter("jvm.other", TenantMetricsConfig.TENANT_TAG, "2").increment();

        assertThat(registry.find("tms.outbox.relay.passes").counter()).isNotNull();
        assertThat(registry.find("jvm.other").counter()).isNotNull();
    }
}
