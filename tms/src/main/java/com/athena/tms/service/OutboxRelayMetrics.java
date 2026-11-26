package com.athena.tms.service;

import static com.athena.tms.config.TenantMetricsConfig.TENANT_TAG;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * relay 的指標。lag 是每個租戶從寫入 outbox 到 Kafka 確認收到的時間，用來看有沒有哪個租戶被別的租戶拖慢。
 * 每個租戶都有自己的一組時間序列，所以直方圖只留固定幾個門檻，不開完整百分位。
 */
@Component
class OutboxRelayMetrics {

    private static final Duration[] LAG_BUCKETS = {
            Duration.ofMillis(50), Duration.ofMillis(250), Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30)};

    private final MeterRegistry registry;
    private final Counter passes;

    OutboxRelayMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.passes = Counter.builder("tms.outbox.relay.passes").description("relay 跑完的租戶輪數").register(registry);
    }

    void readyHotels(Supplier<Number> readyCount) {
        Gauge.builder("tms.outbox.relay.ready.hotels", readyCount).description("排隊等 relay 的租戶數").register(registry);
    }

    void sent(long tenantId, String kind, Duration lag) {
        Counter.builder("tms.outbox.relay.sent").tag(TENANT_TAG, Long.toString(tenantId)).tag("kind", kind)
                .register(registry).increment();
        Timer.builder("tms.outbox.relay.lag").tag(TENANT_TAG, Long.toString(tenantId)).tag("kind", kind)
                .serviceLevelObjectives(LAG_BUCKETS).register(registry).record(lag);
    }

    void sendFailed(long tenantId, String kind) {
        Counter.builder("tms.outbox.relay.send.failures").tag(TENANT_TAG, Long.toString(tenantId)).tag("kind", kind)
                .register(registry).increment();
    }

    void exhausted(long tenantId, String kind) {
        Counter.builder("tms.outbox.relay.exhausted").tag(TENANT_TAG, Long.toString(tenantId)).tag("kind", kind)
                .register(registry).increment();
    }

    void turn(long tenantId, HotelRunQueue.TurnOutcome outcome, Duration took) {
        passes.increment();
        Timer.builder("tms.outbox.relay.turn").tag(TENANT_TAG, Long.toString(tenantId))
                .tag("outcome", outcome.name().toLowerCase(Locale.ROOT))
                .register(registry).record(took);
    }
}
