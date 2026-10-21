package com.athena.tms.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.RecordBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.athena.tms.dto.IntegrationRoomStatusEvent;
import com.athena.tms.dto.TaskChange;

/**
 * SSE 推送的 Kafka 下游：訂閱 tms.task-events，按身分過濾後分發給連線中的
 * 住客、員工與 PMS 整合客戶端。**只讀不回寫**，這裡的 DB 動作只有查任務
 * 歸屬做路由、查事件列與房號組對外 payload。
 *
 * <p><b>為什麼 consumer group 要每台主機各自一個</b>：Kafka 的規則是同一個
 * consumer group 裡的成員會分攤 partition（partition 數有限，instance 數一多，
 * 分不到 partition 的那些就什麼都收不到）。但 SSE 推送的需求剛好相反——
 * 使用者的瀏覽器連線落在哪台主機，那台主機就要能把對應事件推給他；
 * 如果某台主機因為分攤而漏收了某個 partition 的事件，連在它上面的使用者
 * 就永遠收不到那幾筆鈴聲，而且不會有任何錯誤訊息，純粹悄悄漏推。
 * 所以每個 instance 要用獨立、唯一的 group id，讓 Kafka 把同一份事件完整
 * 複製一份給「每一個 group」（也就是每一台主機），而不是「每一個 group
 * 的某個成員」。group id 在啟動時隨機產生，重啟也會換一個新的。
 *
 * <p>正因為每次啟動都是全新的 group（Kafka 對它來說從沒消費過任何東西），
 * auto.offset.reset 顯式設成 latest：新 group 預設只從「現在」開始收，
 * 不會把整個 topic 的歷史事件重播一次灌給剛連上的使用者。但這也表示部署
 * 重啟那段窗口（舊 instance 已停、新 instance 的 group 還沒接上）實際發生的
 * 事件，新 instance 永遠不會收到——這個洞不是靠調這支 consumer 的設定補起來
 * 的，是 StreamConnection.sendResync 在每條新 SSE 連線建立時提醒客戶端
 * 「先重拉一次快照」把它蓋過去。
 *
 * <p>relay 是 at-least-once：送出成功但標 sent 之前當掉會重送。
 * 所以以 envelope 的 (task_id, seq) 去重：同任務已推過的 seq 不再推，
 * 重複事件在這裡吸收。去重簿刻意只放程序內有界 LRU、不上持久化——客戶端
 * 收鈴後的動作（重拉快照）天生冪等，去重狀態遺失的最壞後果只是多響一次鈴。
 *
 * <p>任何一筆事件處理失敗只記 log 不拋出：拋出會讓容器重試、卡住整個
 * partition 的消費——SSE 容許掉事件（真相在資料庫），卡輸送帶比掉鈴嚴重。
 */
@Component
public class TaskEventsSseConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskEventsSseConsumer.class);

    /** 去重簿上限：一萬個近期任務夠覆蓋重送窗口（重送都在秒級），再大只是佔記憶體。 */
    private static final int DEDUP_CAPACITY = 10_000;

    private final StreamConnectionRegistry registry;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Duration degradedThreshold;
    private final KafkaListenerEndpointRegistry kafkaListenerRegistry;

    /** degraded 只在「越過門檻的那一刻」廣播一次：每筆遲到事件都廣播會把客戶端淹死。 */
    private final AtomicBoolean degraded = new AtomicBoolean(false);

    /** task_id→已推過的最大 seq；access-order LRU，超量淘汰最久沒碰的任務。 */
    private final Map<UUID, Integer> deliveredSeqByTask =
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) {
                    return size() > DEDUP_CAPACITY;
                }
            };

    public TaskEventsSseConsumer(StreamConnectionRegistry registry,
                                 TenantScopedExecutor tenantScopedExecutor,
                                 JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 @Value("${tms.stream.degraded-threshold:10s}") String degradedThreshold,
                                 KafkaListenerEndpointRegistry kafkaListenerRegistry) {
        this.registry = registry;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.degradedThreshold = DurationStyle.detectAndParse(degradedThreshold);
        this.kafkaListenerRegistry = kafkaListenerRegistry;
    }

    /**
     * groupId 是 "${...}-#{T(java.util.UUID).randomUUID()}" 這種屬性佔位符與
     * SpEL 混用的寫法，紙上推演容易看錯；啟動後直接把容器實際拿到的 group id
     * 印出來，才是這個表達式真的生效、而不是被原樣當字面字串用掉的證據。
     */
    @EventListener(ApplicationReadyEvent.class)
    void logResolvedGroupId() {
        MessageListenerContainer container = kafkaListenerRegistry.getListenerContainer("sse-task-events");
        if (container != null) {
            log.info("SSE consumer 這個 instance 實際使用的 Kafka consumer group id：{}", container.getGroupId());
        }
    }

    @KafkaListener(id = "sse-task-events",
            topics = "${tms.kafka.topic.task-events}",
            // 前綴可用 tms.stream.consumer-group 設定覆寫（例如在 k8s 想塞 pod name
            // 方便看 log），但不管前綴是什麼，一定要再補一段啟動時隨機產生的值——
            // 這才是「每個 instance 唯一」真正生效的部分，光改前綴還是會撞名。
            // auto.offset.reset 不在這裡覆寫、改走 spring.kafka.consumer.auto-offset-reset
            // 全域設定（見 application.properties）：這支 listener 用 properties 屬性
            // 覆寫的話，測試環境特意設的 earliest（CucumberSpringConfiguration 有說明
            // 理由）會被蓋掉，等於繞過了測試刻意要的安全網。
            groupId = "${tms.stream.consumer-group:sse}-#{T(java.util.UUID).randomUUID()}")
    public void onRecord(ConsumerRecord<String, String> record) {
        try {
            trackConsumerLag(record.timestamp());
            dispatch(record.value());
        } catch (Exception e) {
            log.warn("SSE 分發失敗，略過此事件（offset={}，掉鈴可接受、真相在 DB）: {}",
                    record.offset(), e.getMessage());
        }
    }

    /**
     * 消費延遲＝現在−record 時間戳（CreateTime，relay 送出當下）。超門檻→推
     * degraded（客戶端轉 5 秒輪詢）；回到門檻內就重置，下次再超會再通知——
     * 降級不是一次性的，broker 反覆抖動客戶端也要反覆得知。
     */
    private void trackConsumerLag(long recordTimestamp) {
        if (recordTimestamp <= RecordBatch.NO_TIMESTAMP) {
            return;
        }
        long lagMillis = System.currentTimeMillis() - recordTimestamp;
        if (lagMillis > degradedThreshold.toMillis()) {
            if (degraded.compareAndSet(false, true)) {
                registry.broadcastDegraded();
            }
        } else {
            degraded.set(false);
        }
    }

    private void dispatch(String envelopeJson) {
        JsonNode envelope = objectMapper.readTree(envelopeJson);
        JsonNode tenantId = envelope.get("tenant_id");
        JsonNode taskId = envelope.get("task_id");
        JsonNode seq = envelope.get("seq");
        JsonNode type = envelope.get("type");
        if (tenantId == null || taskId == null || seq == null || type == null) {
            // 同 topic 只該有 task_event envelope；形狀不對就略過，不賭欄位半殘的事件
            log.debug("envelope 缺必要欄位，略過: {}", envelopeJson);
            return;
        }
        UUID task = UUID.fromString(taskId.asText());
        int eventSeq = seq.asInt();
        if (alreadyDelivered(task, eventSeq)) {
            return;
        }
        JsonNode occurredAt = envelope.get("occurred_at");
        TaskChange change = loadRouting(tenantId.asLong(), task, eventSeq, type.asText(),
                occurredAt == null ? null : occurredAt.asText());
        if (change == null) {
            log.debug("任務 {} 查無投影列，略過此鈴", task);
            return;
        }
        registry.broadcastTask(change);
        pushIntegration(tenantId.asLong(), task, eventSeq, type.asText());
    }

    /**
     * 整合連線（PMS）的即時推送：事件種類命中對外白名單才產 room_status 事件，
     * 未命中（例如查房未通過）整合連線一筆都不收，guest/staff 的廣播不受影響。
     *
     * <p>路由查詢任何失敗（含事件列尚不可見的時序差）只記 log 不拋出：
     * 即時推送漏一筆，PMS 下次重連帶 cursor 回放就補得回來；拋出反而會
     * 讓這筆事件的 guest/staff 分發連帶被記成失敗。
     */
    private void pushIntegration(long tenantId, UUID taskId, int seq, String type) {
        String hkState = IntegrationRoomStatusWhitelist.hkStateFor(type);
        if (hkState == null) {
            return;
        }
        try {
            IntegrationRoomStatusEvent event = loadRoomStatus(tenantId, taskId, seq, hkState);
            if (event == null) {
                log.debug("任務 {} seq {} 查無事件列或房號，整合推送略過（回放兜底）", taskId, seq);
                return;
            }
            registry.broadcastRoomStatus(tenantId, event);
        } catch (Exception e) {
            log.warn("整合推送查詢失敗，略過此事件（task={} seq={}，回放兜底）: {}",
                    taskId, seq, e.getMessage());
        }
    }

    /**
     * 以 (tenant_id, task_id, seq) 取事件列的 id（對外 event_id 兼 cursor）、
     * 以任務 join rooms 取 room_no。join 條件連 tenant_id 一起比對，和類別層
     * 的租戶授權同一道理：跨租戶 join 一旦成立就是靜默外洩。
     */
    private IntegrationRoomStatusEvent loadRoomStatus(long tenantId, UUID taskId, int seq, String hkState) {
        return tenantScopedExecutor.callAsTenant(tenantId, () -> {
            List<IntegrationRoomStatusEvent> rows = jdbcTemplate.query("""
                    SELECT te.id, te.created_at, r.room_no
                      FROM task_events te
                      JOIN tasks t ON t.tenant_id = te.tenant_id AND t.id = te.task_id
                      JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                     WHERE te.tenant_id = ? AND te.task_id = ? AND te.seq = ?
                    """,
                    (rs, rowNum) -> new IntegrationRoomStatusEvent(
                            IntegrationRoomStatusEvent.SCHEMA_V1,
                            Long.toString(rs.getLong("id")),
                            rs.getString("room_no"),
                            hkState,
                            taskId,
                            seq,
                            rs.getObject("created_at", OffsetDateTime.class)),
                    tenantId, taskId, seq);
            return rows.isEmpty() ? null : rows.getFirst();
        });
    }

    /** (task_id, seq) 去重：已推過的 seq（含更早的）直接吸收——relay at-least-once 的重複在此落地。 */
    private boolean alreadyDelivered(UUID taskId, int seq) {
        synchronized (deliveredSeqByTask) {
            Integer seen = deliveredSeqByTask.get(taskId);
            if (seen != null && seen >= seq) {
                return true;
            }
            deliveredSeqByTask.put(taskId, seq);
            return false;
        }
    }

    /**
     * 查 tasks 當下歸屬做路由。Kafka 消費執行緒沒有請求脈絡，tasks 有 RLS，
     * 必須走 TenantScopedExecutor 的租戶交易，否則 RLS 下查到零列；
     * 租戶 id 來自 envelope 自述，這也是消費端過租戶授權的依據。
     */
    private TaskChange loadRouting(long tenantId, UUID taskId, int seq, String type, String occurredAt) {
        return tenantScopedExecutor.callAsTenant(tenantId, () -> {
            List<TaskChange> rows = jdbcTemplate.query("""
                    SELECT dept, assignee_id, reservation_id
                      FROM tasks
                     WHERE tenant_id = ? AND id = ?
                    """,
                    (rs, rowNum) -> new TaskChange(tenantId, taskId, seq, type, occurredAt,
                            rs.getString("dept"),
                            rs.getObject("assignee_id", UUID.class),
                            rs.getObject("reservation_id", UUID.class)),
                    tenantId, taskId);
            return rows.isEmpty() ? null : rows.getFirst();
        });
    }
}
