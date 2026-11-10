package com.athena.tms.bdd.steps.push;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.KafkaGroupAwait;
import com.athena.tms.bdd.support.OutboxAwait;
import com.athena.tms.bdd.support.RecordingPushSender;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.common.OutboxKafkaHeaders;
import com.athena.tms.config.KafkaTopicsProperties;
import io.cucumber.java.zh_tw.假設;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 推播送達手機的句型（dsl.md 推播送達段落）：手機登記／解除、送達紀錄、推播服務替身的斷言。
 * 手機的推播地址由本類別依「手機識別碼＋第幾次登記」自己編，每次登記換一個新地址，
 * 情境才分得出「地址為最新一次」。
 */
public class PushDeliverySteps {

    private static final Map<String, String> STATUS_TERMS = Map.of(
            "已送出", "sent",
            "沒有可用裝置", "skipped_no_token",
            "送出失敗", "failed");

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final OutboxAwait outboxAwait;
    private final TenantFixtures tenantFixtures;
    private final RecordingPushSender pushSender;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    private final KafkaGroupAwait kafkaGroupAwait;
    private final ObjectMapper objectMapper;

    /** (員工, 手機) → 目前登記的推播地址；同一組每登記一次換一個地址。 */
    private final Map<String, String> tokens = new HashMap<>();
    private final Map<String, Integer> registrations = new HashMap<>();

    public PushDeliverySteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            OutboxAwait outboxAwait,
            TenantFixtures tenantFixtures,
            RecordingPushSender pushSender,
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaTopicsProperties topics,
            KafkaGroupAwait kafkaGroupAwait,
            ObjectMapper objectMapper) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.outboxAwait = outboxAwait;
        this.tenantFixtures = tenantFixtures;
        this.pushSender = pushSender;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.kafkaGroupAwait = kafkaGroupAwait;
        this.objectMapper = objectMapper;
    }

    @假設("{string} 的手機 {string} 已登記推播")
    public void staffRegisteredDevice(String staffName, String fingerprint) {
        register(staffName, fingerprint);
    }

    @當("{string} 再次登記手機 {string} 的推播")
    public void staffRegistersDeviceAgain(String staffName, String fingerprint) {
        register(staffName, fingerprint);
    }

    @當("{string} 的手機 {string} 登出解除推播")
    public void staffRevokesDevice(String staffName, String fingerprint) {
        ScenarioWorld.ApiResponse resp = apiClient.exchange(
                HttpMethod.DELETE, "/me/push-token", BddApiClient.Auth.staff(world.jwt(staffName)),
                Map.of("deviceFingerprint", fingerprint), null);
        if (resp.status() != 204) {
            throw new AssertionError("解除推播登記預期 204，實際 " + resp.status() + "：" + resp.body());
        }
    }

    /** 讓推播服務替身對這台手機目前的地址回報「已失效」（模擬 FCM 的 UNREGISTERED）。 */
    @假設("推播服務會回報 {string} 手機 {string} 的地址已失效")
    public void pushServiceRejectsDevice(String staffName, String fingerprint) {
        pushSender.markTokenInvalid(currentToken(staffName, fingerprint));
    }

    /**
     * 模擬 Kafka 重投遞：把 outbox 裡那筆推播原樣（同 key、同 header、同 payload）再送上 topic
     * 一次，然後等消費者把它讀掉，後面的斷言才有意義。
     */
    @當("{string} 的推播 {string} 被重送一次")
    public void pushRedelivered(String staffName, String title) throws Exception {
        Map<String, Object> outbox = outboxRowFor(staffName, title);
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topics.getPush(), String.valueOf(outbox.get("aggregate_key")), String.valueOf(outbox.get("payload")));
        record.headers().add(new RecordHeader(
                OutboxKafkaHeaders.TENANT_ID,
                String.valueOf(outbox.get("tenant_id")).getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(
                OutboxKafkaHeaders.IDEMPOTENCY_KEY,
                String.valueOf(outbox.get("idempotency_key")).getBytes(StandardCharsets.UTF_8)));
        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
        if (!kafkaGroupAwait.awaitCaughtUp("tms-push-sender", topics.getPush(), Duration.ofSeconds(15))) {
            throw new AssertionError("推播消費者 15 秒內沒有讀完重送的訊息");
        }
    }

    /**
     * dsl.md 必查：權威狀態——push_deliveries 有這把冪等鍵的一列，status 等於指定狀態。
     * 冪等鍵從 outbox 那筆推播取（收件人＋標題找出那一列）。消費者是非同步的，用輪詢等待。
     */
    @那麼("{string} 的推播 {string} 送達紀錄為 {string}")
    public void deliveryRecordStatus(String staffName, String title, String statusTerm) {
        String expected = STATUS_TERMS.get(statusTerm);
        if (expected == null) {
            throw new IllegalArgumentException("送達紀錄狀態只支援 " + STATUS_TERMS.keySet() + "，實際=" + statusTerm);
        }
        String idempotencyKey = String.valueOf(outboxRowFor(staffName, title).get("idempotency_key"));
        Map<String, Object> row = outboxAwait.waitForRow(
                staffName + " 的推播「" + title + "」送達紀錄狀態為 " + expected,
                "SELECT * FROM push_deliveries WHERE tenant_id = ? AND idempotency_key = ? AND status = ?",
                tenantFixtures.testTenantId(), idempotencyKey, expected);
        if (!UUID.fromString(world.staffId(staffName)).equals(row.get("recipient_staff_id"))) {
            throw new AssertionError("送達紀錄的收件人不是 " + staffName + "：" + row);
        }
    }

    @那麼("推播服務收到送往 {string} 手機 {string} 的 {string}")
    public void pushServiceReceived(String staffName, String fingerprint, String title) {
        String token = currentToken(staffName, fingerprint);
        List<RecordingPushSender.Sent> sent = pushSender.sentTo(token, title);
        if (sent.size() != 1) {
            throw new AssertionError("預期推播服務收到送往 " + staffName + " 手機 " + fingerprint
                    + " 的「" + title + "」恰好一次，實際 " + sent.size() + " 次");
        }
        RecordingPushSender.Sent only = sent.getFirst();
        if (!only.device().isIos()) {
            throw new AssertionError("登記時平台是 ios，送出時卻是 " + only.device().platform());
        }
        if (only.message().targetJson() == null || !only.message().targetJson().contains("\"kind\"")) {
            throw new AssertionError("送往裝置的訊息沒有帶 PushTarget：" + only.message());
        }
    }

    @那麼("{string} 的推播 {string} 送達紀錄只有一筆，推播服務也只送出一次")
    public void deliveredExactlyOnce(String staffName, String title) {
        String idempotencyKey = String.valueOf(outboxRowFor(staffName, title).get("idempotency_key"));
        long rows = dbProbe.count(
                "SELECT count(*) FROM push_deliveries WHERE tenant_id = ? AND idempotency_key = ?",
                tenantFixtures.testTenantId(), idempotencyKey);
        if (rows != 1) {
            throw new AssertionError("送達紀錄預期 1 筆，實際 " + rows + " 筆（冪等鍵 " + idempotencyKey + "）");
        }
        UUID staffId = UUID.fromString(world.staffId(staffName));
        long sends = pushSender.sentWithTitle(title).stream()
                .filter(s -> staffId.equals(s.message().recipientStaffId()))
                .count();
        if (sends != 1) {
            throw new AssertionError("推播服務預期只收到 " + staffName + " 的「" + title + "」一次，實際 " + sends + " 次");
        }
    }

    @那麼("{string} 名下的手機 {string} 只有一筆登記，地址為最新一次")
    public void singleDeviceRowWithLatestToken(String staffName, String fingerprint) {
        List<Map<String, Object>> rows = deviceRows(staffName, fingerprint);
        if (rows.size() != 1) {
            throw new AssertionError(staffName + " 的手機 " + fingerprint + " 預期 1 筆登記，實際 " + rows.size());
        }
        Map<String, Object> row = rows.getFirst();
        String expectedToken = currentToken(staffName, fingerprint);
        if (!expectedToken.equals(row.get("push_token"))) {
            throw new AssertionError("推播地址預期最新一次的 " + expectedToken + "，實際 " + row.get("push_token"));
        }
        if (row.get("revoked_at") != null) {
            throw new AssertionError("重新登記後 revoked_at 應為空，實際 " + row.get("revoked_at"));
        }
    }

    @那麼("{string} 的手機 {string} 已解除推播登記")
    public void deviceRevoked(String staffName, String fingerprint) {
        List<Map<String, Object>> rows = deviceRows(staffName, fingerprint);
        if (rows.size() != 1) {
            throw new AssertionError(staffName + " 的手機 " + fingerprint + " 預期 1 筆登記，實際 " + rows.size());
        }
        if (rows.getFirst().get("revoked_at") == null) {
            throw new AssertionError(staffName + " 的手機 " + fingerprint + " 預期已解除（revoked_at 非空），實際為空");
        }
    }

    private void register(String staffName, String fingerprint) {
        String key = staffName + "/" + fingerprint;
        int n = registrations.merge(key, 1, Integer::sum);
        String token = "fcm-token-" + fingerprint + "-" + n;
        tokens.put(key, token);
        ScenarioWorld.ApiResponse resp = apiClient.exchange(
                HttpMethod.PUT, "/me/push-token", BddApiClient.Auth.staff(world.jwt(staffName)),
                Map.of("deviceFingerprint", fingerprint, "pushToken", token, "platform", "ios"), null);
        if (resp.status() != 204) {
            throw new AssertionError("登記推播預期 204，實際 " + resp.status() + "：" + resp.body());
        }
    }

    private String currentToken(String staffName, String fingerprint) {
        String token = tokens.get(staffName + "/" + fingerprint);
        if (token == null) {
            throw new IllegalStateException(staffName + " 的手機 " + fingerprint + " 在本情境還沒登記過推播");
        }
        return token;
    }

    private List<Map<String, Object>> deviceRows(String staffName, String fingerprint) {
        return dbProbe.queryMaps(
                "SELECT * FROM staff_devices WHERE tenant_id = ? AND staff_id = ? AND device_fingerprint = ?",
                tenantFixtures.testTenantId(), UUID.fromString(world.staffId(staffName)), fingerprint);
    }

    /**
     * 等 outbox 出現送給該員工、該標題的那筆推播，回傳整列（含 idempotency_key）。
     * payload 由資料庫以 jsonb 吐回，欄位間的空白不固定，所以解析成 JSON 再比對，不做字串比對。
     */
    private Map<String, Object> outboxRowFor(String staffName, String title) {
        String staffId = world.staffId(staffName);
        return outboxAwait.waitForOutboxRow(
                "push",
                row -> {
                    JsonNode payload = objectMapper.readTree(String.valueOf(row.get("payload")));
                    return staffId.equals(payload.path("recipientStaffId").asText())
                            && title.equals(payload.path("title").asText());
                },
                staffName + " 的推播「" + title + "」已寫入 outbox");
    }
}
