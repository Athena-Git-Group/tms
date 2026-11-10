package com.athena.tms.push;

import com.athena.tms.common.OutboxKafkaHeaders;
import com.athena.tms.service.TenantScopedExecutor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 推播消費者：讀 Kafka 的 push topic，把每一筆推播送到收件人的每一台裝置，並寫一列送達紀錄。
 *
 * <h2>要保證什麼</h2>
 * <ol>
 *   <li><b>同一筆推播最多讓裝置響一次。</b>relay 可能在「送達 Kafka 但沒標 sent」時重送，
 *       Kafka 也可能因為 rebalance 重投遞；手機因此響兩次會讓員工不信任推播。</li>
 *   <li><b>同一位收件人的推播照 outbox 寫入順序送。</b>Kafka 用 aggregate_key 分 partition，
 *       這裡用固定 consumer group、每筆處理完才提交 offset，重試時停在原地不跳過。</li>
 *   <li><b>一筆壞訊息不能卡死整個 topic。</b>暫時性錯誤退避重試幾次後改送 dead letter topic，
 *       後面的訊息繼續。</li>
 * </ol>
 *
 * <h2>怎麼做</h2>
 * 冪等鍵與租戶 id 從 relay 附的 header 取（{@link OutboxKafkaHeaders}）。處理前先查
 * push_deliveries：已經是 sent 或 skipped_no_token 就跳過；failed（上一次暫時性錯誤後重試）
 * 或沒有紀錄才真的送。送完一次寫一列，狀態依裝置結果彙總：有任何一台成功＝sent；沒有可用
 * 裝置＝skipped_no_token；全部永久失敗＝failed。任何一台遇到暫時性錯誤就先把紀錄寫成 failed
 * 再把例外丟出去，交給容器的錯誤處理器重試；重試成功後同一列會被覆寫成 sent。
 * 推播服務回報地址失效的裝置當場標撤銷，下一筆推播就不會再送它。
 *
 * <p>沒有 header、或 payload 不是 PushPayload 形狀的訊息（例如 relay 改版前送出的、或測試
 * 直接塞的假資料）記 warn 後略過，不重試也不送 dead letter，因為重試不會讓它變得可解析。
 *
 * <h2>刻意不做的</h2>
 * 送出與寫紀錄不在同一個交易：推播服務的網路呼叫不該佔著資料庫連線。代價是「送出成功但寫紀錄
 * 前當機」會在重啟後再送一次，屬於 at-least-once，由第一條保證的去重把機率壓到只剩這個窗口。
 */
@Component
public class PushDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(PushDeliveryConsumer.class);

    private final PushDeliveryRecorder recorder;
    private final PushSender pushSender;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final ObjectMapper objectMapper;

    public PushDeliveryConsumer(
            PushDeliveryRecorder recorder,
            PushSender pushSender,
            TenantScopedExecutor tenantScopedExecutor,
            ObjectMapper objectMapper) {
        this.recorder = recorder;
        this.pushSender = pushSender;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.objectMapper = objectMapper;
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
        deliver(tenantId, message);
    }

    /**
     * 每一句資料庫讀寫各自一個交易，不把兩張表的鎖抓在同一個交易裡：這條執行緒和別的交易
     * （例如測試在情境之間整批清表）同時動 staff_devices 與 push_deliveries 時才不會互相等成死結。
     * 送出本身在任何交易之外。
     */
    private void deliver(long tenantId, PushMessage message) {
        Optional<String> status = tenantScopedExecutor.callAsTenant(
                tenantId, () -> recorder.statusOf(tenantId, message.idempotencyKey()));
        if (status.isPresent() && !PushDeliveryRecorder.STATUS_FAILED.equals(status.get())) {
            log.info("push 冪等鍵 {} 已處理過（{}），跳過重複投遞", message.idempotencyKey(), status.get());
            return;
        }
        List<PushDeviceAddress> devices = tenantScopedExecutor.callAsTenant(
                tenantId, () -> recorder.activeDevices(tenantId, message.recipientStaffId()));
        if (devices.isEmpty()) {
            tenantScopedExecutor.runAsTenant(tenantId, () -> recorder.record(
                    tenantId, message.idempotencyKey(), message.recipientStaffId(),
                    PushDeliveryRecorder.STATUS_SKIPPED_NO_TOKEN, null));
            return;
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
