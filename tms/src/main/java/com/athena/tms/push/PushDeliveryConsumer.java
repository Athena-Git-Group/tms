package com.athena.tms.push;

import com.athena.tms.common.OutboxKafkaHeaders;
import com.athena.tms.config.KafkaTopicsProperties;
import com.athena.tms.config.TenantMetricsConfig;
import com.athena.tms.service.TenantScopedExecutor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class PushDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(PushDeliveryConsumer.class);

    private final PushDeliveryRecorder recorder;
    private final PushSender pushSender;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final ObjectMapper objectMapper;
    private final KafkaTopicsProperties topics;
    private final MeterRegistry meterRegistry;

    public PushDeliveryConsumer(
            PushDeliveryRecorder recorder,
            PushSender pushSender,
            TenantScopedExecutor tenantScopedExecutor,
            ObjectMapper objectMapper,
            KafkaTopicsProperties topics,
            MeterRegistry meterRegistry) {
        this.recorder = recorder;
        this.pushSender = pushSender;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.objectMapper = objectMapper;
        this.topics = topics;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            id = "push-delivery",
            topics = "${tms.kafka.topic.push}",
            groupId = "tms-push-sender",
            containerFactory = PushDeliveryKafkaConfig.LISTENER_FACTORY)
    public void onRecord(ConsumerRecord<String, String> record) {
        String idempotencyKey = header(record, OutboxKafkaHeaders.IDEMPOTENCY_KEY);
        String tenantHeader = header(record, OutboxKafkaHeaders.TENANT_ID);
        if (idempotencyKey == null || tenantHeader == null) {
            log.warn("push 訊息缺少租戶或冪等鍵 header，略過（offset={} key={}）", record.offset(), record.key());
            return;
        }
        long tenantId = Long.parseLong(tenantHeader);
        PushMessage message = parse(idempotencyKey, record.value());
        if (message == null) {
            log.warn("push 訊息 payload 不是 PushPayload 形狀，略過（offset={} 冪等鍵={}）", record.offset(), idempotencyKey);
            return;
        }
        String tenant = Long.toString(tenantId);
        if (topics.getPush().equals(record.topic())) {
            recordQueueWait(tenant, record);
        }
        try {
            countDelivery(tenant, deliver(tenantId, message));
        } catch (PushTransientException e) {
            countDelivery(tenant, "transient_failure");
            throw e;
        }
    }

    public void onDeadLetter(ConsumerRecord<String, String> record) {
        String tenantHeader = header(record, OutboxKafkaHeaders.TENANT_ID);
        countDelivery(tenantHeader == null ? "unknown" : tenantHeader, "dead_lettered");
        log.error("push 訊息重試用盡，留在 {}（飯店 {}，冪等鍵 {}，key={}）：{}",
                record.topic(), tenantHeader,
                header(record, OutboxKafkaHeaders.IDEMPOTENCY_KEY), record.key(),
                header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE));
    }

    private String deliver(long tenantId, PushMessage message) {
        Optional<String> status = tenantScopedExecutor.callAsTenant(
                tenantId, () -> recorder.statusOf(tenantId, message.idempotencyKey()));
        if (status.isPresent() && !PushDeliveryRecorder.STATUS_FAILED.equals(status.get())) {
            log.info("push 冪等鍵 {} 已處理過（{}），跳過重複投遞", message.idempotencyKey(), status.get());
            return "duplicate";
        }
        List<PushDeviceAddress> devices = tenantScopedExecutor.callAsTenant(
                tenantId, () -> recorder.activeDevices(tenantId, message.recipientStaffId()));
        if (devices.isEmpty()) {
            tenantScopedExecutor.runAsTenant(tenantId, () -> recorder.record(
                    tenantId, message.idempotencyKey(), message.recipientStaffId(),
                    PushDeliveryRecorder.STATUS_SKIPPED_NO_TOKEN, null));
            return PushDeliveryRecorder.STATUS_SKIPPED_NO_TOKEN;
        }

        boolean anySent = false;
        String lastError = null;
        List<UUID> invalidDevices = new ArrayList<>();
        for (PushDeviceAddress device : devices) {
            PushSendResult result;
            try {
                result = pushSender.send(message, device);
            } catch (PushTransientException e) {
                revokeAll(tenantId, invalidDevices);
                String error = e.getMessage();
                tenantScopedExecutor.runAsTenant(tenantId, () -> recorder.record(
                        tenantId, message.idempotencyKey(), message.recipientStaffId(),
                        PushDeliveryRecorder.STATUS_FAILED, error));
                throw e;
            }
            switch (result.outcome()) {
                case SENT -> anySent = true;
                case INVALID_TOKEN -> {
                    invalidDevices.add(device.deviceId());
                    lastError = result.error();
                }
                case FAILED -> lastError = result.error();
            }
        }

        revokeAll(tenantId, invalidDevices);
        String finalStatus = anySent ? PushDeliveryRecorder.STATUS_SENT : PushDeliveryRecorder.STATUS_FAILED;
        String error = lastError;
        tenantScopedExecutor.runAsTenant(tenantId, () -> recorder.record(
                tenantId, message.idempotencyKey(), message.recipientStaffId(), finalStatus, error));
        return finalStatus;
    }

    private void countDelivery(String tenant, String outcome) {
        Counter.builder("tms.push.deliveries").tag(TenantMetricsConfig.TENANT_TAG, tenant).tag("outcome", outcome)
                .register(meterRegistry).increment();
    }

    // 從 relay 送上 Kafka 到消費者開始處理的等待時間，用來看一家飯店的推播有沒有被別家飯店的推播擋在後面。
    // 只量第一次投遞，重試 topic 的等待包含刻意的重試間隔，混進來就看不出排隊。
    private void recordQueueWait(String tenant, ConsumerRecord<String, String> record) {
        Timer.builder("tms.push.queue.wait").tag(TenantMetricsConfig.TENANT_TAG, tenant)
                .serviceLevelObjectives(Duration.ofMillis(250), Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30))
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0, System.currentTimeMillis() - record.timestamp())));
    }

    private void revokeAll(long tenantId, List<UUID> deviceIds) {
        for (UUID deviceId : deviceIds) {
            tenantScopedExecutor.runAsTenant(tenantId, () -> recorder.revokeDevice(tenantId, deviceId));
        }
    }

    private PushMessage parse(String idempotencyKey, String payloadJson) {
        try {
            JsonNode payload = objectMapper.readTree(payloadJson);
            JsonNode recipient = payload.get("recipientStaffId");
            JsonNode event = payload.get("event");
            JsonNode title = payload.get("title");
            JsonNode target = payload.get("target");
            if (recipient == null || event == null || title == null || target == null || !target.isObject()) {
                return null;
            }
            String body = payload.hasNonNull("body") ? payload.get("body").asText() : null;
            return new PushMessage(
                    idempotencyKey,
                    UUID.fromString(recipient.asText()),
                    event.asText(),
                    title.asText(),
                    body,
                    target.toString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
