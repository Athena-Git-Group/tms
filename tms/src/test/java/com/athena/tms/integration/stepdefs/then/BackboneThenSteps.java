package com.athena.tms.integration.stepdefs.then;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.Awaits;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.KafkaProbe;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.integration.stepdefs.utils.Tenants;
import com.athena.tms.integration.stepdefs.utils.TestData;
import io.cucumber.java.en.Then;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class BackboneThenSteps {
    private final ScenarioContext ctx;
    private final TaskFixtures fixtures;
    private final DbSupport db;
    private final ObjectMapper objectMapper;

    public BackboneThenSteps(ScenarioContext ctx, TaskFixtures fixtures, DbSupport db, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.fixtures = fixtures;
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Then("^每次都收到與第一次相同的狀態碼與回應$")
    public void everyResendMatchesFirstResponse() {
        ScenarioContext.HttpResult first = ctx.firstResponse();
        assertFalse(ctx.replays().isEmpty(), "When 步驟該留下重送結果");
        int nth = 1;
        for (ScenarioContext.HttpResult replay : ctx.replays()) {
            assertEquals(first.status(), replay.status(), "重送第 " + nth + " 次：狀態碼一致");
            assertEquals(first.body(), replay.body(), "重送第 " + nth + " 次：body 一致");
            assertEquals("true", replay.replayedHeader(), "重送走 processed_ops 回放");
            nth++;
        }
        assertEquals(1, db.processedOpsCount(1L, ctx.opId()), "冪等簿有且只有這把鍵");
    }

    @Then("^任務只被完成一次、事件只寫一筆、推播只排一筆$")
    public void completedExactlyOnce() {
        UUID id = ctx.taskId();
        assertEquals(1, db.eventCountOfType(id, "completed"), "completed 事件只寫一筆");
        Map<String, Object> row = db.taskRow(id);
        assertEquals("done", row.get("state"));
        assertEquals(4, ((Number) row.get("event_seq")).intValue(), "回放不佔 seq");
        assertEquals(4, db.outboxCount("task:" + id), "outbox 只排一輪（created..completed 各一，回放後零增量）");
    }

    @Then("^該轉移以單一條件式 UPDATE 完成，重複再轉即更新 0 列、該請求應該失敗，回應為 409$")
    public void guardRejectsRepeatWith409() {
        StateConflictException conflict = assertThrows(StateConflictException.class,
                () -> Tenants.runAsTenant(1L, () -> fixtures.engine().passInspection(1L, ctx.taskId(), TestData.MG1)),
                "同一轉移第二次該被守衛擋下（更新 0 列）");
        assertEquals(409, conflict.getStatus());
        assertEquals("state_conflict", conflict.getCode());
    }

    @Then("^事件 seq 取自同句 UPDATE 回傳的 event_seq，嚴格遞增且無洞$")
    public void seqComesFromSameStatement() {
        UUID id = ctx.taskId();
        assertEquals(List.of(1, 2, 3, 4, 5), db.eventSeqs(id), "seq 嚴格遞增且無洞");
        assertEquals(5, ctx.returnedSeq(), "引擎回傳的 seq＝守衛同句 RETURNING 的 event_seq");
        assertEquals(5, ((Number) db.taskRow(id).get("event_seq")).intValue());
    }

    @Then("^恰有一次認領成功、另一次請求應該失敗，回應為 409 且原因是 \"state_conflict\"$")
    public void exactlyOneClaimWins() {
        List<String> outcomes = ctx.claimOutcomes();
        assertEquals(1, Collections.frequency(outcomes, "ok"), "恰一次成功: " + outcomes);
        assertEquals(1, Collections.frequency(outcomes, "state_conflict"), "另一次 409: " + outcomes);
    }

    @Then("^任務停在 \"accepted\"、事件只有 created 與 claimed、輸的那次不佔 seq$")
    public void taskAcceptedWithTwoEvents() {
        UUID id = ctx.taskId();
        Map<String, Object> row = db.taskRow(id);
        assertEquals("accepted", row.get("state"));
        assertEquals(TestData.STAFF_HK1, row.get("assignee_id"));
        assertEquals(2, ((Number) row.get("event_seq")).intValue(), "created=1、claimed=2；輸的那次不佔號");
        assertEquals(List.of("created", "claimed"), db.eventTypes(id), "失敗的轉移不得留下事件");
    }

    @Then("^三次請求應該都失敗，回應皆為 409 且原因都是 \"state_conflict\"$")
    public void allThreeRejected() {
        assertEquals(List.of(409, 409, 409), ctx.rejectionStatuses());
        assertEquals(List.of("state_conflict", "state_conflict", "state_conflict"), ctx.rejectionCodes());
    }

    @Then("^被擋的轉移不產生任何事件$")
    public void blockedTransitionsLeaveNoEvents() {
        assertEquals(List.of("created", "claimed"), db.eventTypes(ctx.taskId()), "只有合法的建單與認領留事件");
    }

    @Then("^事件 seq 為 1 到 8 嚴格遞增且無洞$")
    public void seqOneToEightNoGaps() {
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), db.eventSeqs(ctx.taskId()));
        assertEquals(8, ctx.returnedSeq());
    }

    @Then("^created 快照之種類為 \"([a-z_]+)\"、來源為 \"([a-z_]+)\"、房號為 \"(\\d+)\"、標題為 \"([^\"]+)\"，且不含訂單鍵$")
    public void createdSnapshotLiteralValues(String type, String source, String roomNo, String title) {
        JsonNode snapshot = objectMapper.readTree(db.eventPayload(ctx.taskId(), 1));
        assertEquals(type, snapshot.get("type").asText(), "種類");
        assertEquals(source, snapshot.get("source").asText(), "來源");
        String roomInDb = db.admin().queryForObject("SELECT room_no FROM rooms WHERE id = ?", String.class,
                UUID.fromString(snapshot.get("room_id").asText()));
        assertEquals(roomNo, roomInDb, "房號");
        assertEquals(title, snapshot.get("title").asText(), "標題");
        assertFalse(snapshot.has("reservation_id"), "沒綁訂單就不落鍵");
    }

    @Then("^任務停在 \"inspected\"、redo_count 為 1、之後任何轉移的請求應該失敗，回應為 409$")
    public void terminalInspectedState() {
        UUID id = ctx.taskId();
        Map<String, Object> row = db.taskRow(id);
        assertEquals("inspected", row.get("state"));
        assertEquals(1, ((Number) row.get("redo_count")).intValue(), "查房退回一次");
        assertNotNull(row.get("done_at"));
        assertThrows(StateConflictException.class,
                () -> Tenants.runAsTenant(1L, () -> fixtures.engine().passInspection(1L, id, TestData.MG1)),
                "inspected 是終點（E-16）");
    }

    @Then("^事件依序為 created 與 assigned、event_seq 為 2$")
    public void createdThenAssigned() {
        assertEquals(List.of("created", "assigned"), db.eventTypes(ctx.taskId()),
                "排房起點＝created(1)＋assigned(2)，spec §5.2 事件形狀");
        assertEquals(2, ctx.returnedSeq());
    }

    @Then("^任務停在 \"accepted\" 且受派人是「小美」$")
    public void acceptedAssignedToHk1() {
        Map<String, Object> row = db.taskRow(ctx.taskId());
        assertEquals("accepted", row.get("state"));
        assertEquals(TestData.STAFF_HK1, row.get("assignee_id"));
    }

    @Then("^relay 不送出 \"([^\"]+)\" 那筆，直到同 aggregate_key 的 \"([^\"]+)\" 那筆成功$")
    public void blockedKeyStaysPut(String tailKey, String headKey) {
        Long headId = ctx.get("outbox:" + headKey);
        Long tailId = ctx.get("outbox:" + tailKey);
        assertEquals("pending", db.outboxStatus(headId), "等重試的首筆仍 pending");
        assertEquals("pending", db.outboxStatus(tailId), "同鍵後續列必須跟著等，不得先送");
        assertEquals(0, db.outboxAttempts(tailId), "後續列連試都不該試");
        List<ConsumerRecord<String, String>> records = KafkaProbe.consumeAll("tms.push", Duration.ofSeconds(3L));
        ctx.put("pushRecords", records);
        String blockedKey = aggregateKeyOf(headId);
        assertTrue(records.stream().noneMatch(r -> blockedKey.equals(r.key())), "被封鎖鍵整串不得外流");
    }

    @Then("^其他鍵的事件照常轉送、不被擋$")
    public void otherKeysFlow() {
        List<Long> freeIds = ctx.get("outboxFreeIds");
        List<ConsumerRecord<String, String>> records = ctx.get("pushRecords");
        assertNotNull(records, "前一個斷言該先讀過 push topic");
        for (Long freeId : freeIds) {
            assertEquals("sent", db.outboxStatus(freeId));
            String freeKey = aggregateKeyOf(freeId);
            assertTrue(records.stream().anyMatch(r -> freeKey.equals(r.key())), "別鍵事件已上 Kafka: " + freeKey);
        }
    }

    @Then("^這筆 pending 事件仍被轉送上 Kafka、不掉單$")
    public void pendingEventEventuallyDelivered() {
        List<Long> freeIds = ctx.get("outboxFreeIds");
        Long soloId = freeIds.getFirst();
        assertEquals("sent", db.outboxStatus(soloId));
        String soloKey = aggregateKeyOf(soloId);
        List<ConsumerRecord<String, String>> records = KafkaProbe.consumeAll("tms.task-events", Duration.ofSeconds(3L));
        assertTrue(records.stream().anyMatch(r -> soloKey.equals(r.key())), "事件實際到達 Kafka");
    }

    @Then("^待送匣 \"([^\"]+)\" 仍是 \"([^\"]+)\"、attempts 仍是 (\\d+)$")
    public void outboxStaysUntouched(String idempotencyKey, String expectedStatus, int expectedAttempts) throws InterruptedException {
        Thread.sleep(3000L);
        Long outboxId = ctx.get("outbox:" + idempotencyKey);
        assertEquals(expectedStatus, db.outboxStatus(outboxId), "租約／退避還沒到期，不該被任何 instance 動到");
        assertEquals(expectedAttempts, db.outboxAttempts(outboxId), "沒搶到就不該累加 attempts");
    }

    @Then("^待送匣 \"([^\"]+)\" 最終被標記為 \"([^\"]+)\"、attempts 為 (\\d+)$")
    public void outboxReachesStatusWithAttempts(String idempotencyKey, String expectedStatus, int expectedAttempts) {
        Long outboxId = ctx.get("outbox:" + idempotencyKey);
        Awaits.waitUntil(Duration.ofSeconds(20L), "待送匣 " + idempotencyKey + " 該變成 " + expectedStatus,
                () -> expectedStatus.equals(db.outboxStatus(outboxId)));
        assertEquals(expectedAttempts, db.outboxAttempts(outboxId), "attempts 該剛好是這一次 claim 累加的結果");
    }

    @Then("^\"([^\"]+)\" 這個鍵在 Kafka 上有 2 筆內容相同的訊息、重複交由下游去重吸收$")
    public void duplicateIdenticalRecordsOnKafka(String idempotencyKey) {
        Long outboxId = ctx.get("outbox:" + idempotencyKey);
        String key = aggregateKeyOf(outboxId);
        List<ConsumerRecord<String, String>> records = KafkaProbe.consumeAll("tms.task-events", Duration.ofSeconds(3L));
        List<String> values = records.stream().filter(r -> key.equals(r.key())).map(ConsumerRecord::value).toList();
        assertEquals(2, values.size(), "重送前模擬的那一筆＋relay 這輪真正送的那一筆，共兩筆");
        assertEquals(values.get(0), values.get(1), "兩筆內容必須一致，下游才能單靠 (task_id, seq) 去重");
    }

    private String aggregateKeyOf(long outboxId) {
        return db.admin().queryForObject("SELECT aggregate_key FROM outbox WHERE id = ?", String.class, outboxId);
    }

    @Then("^tasks 列未被任何消費者改動，狀態、event_seq、updated_at 均不變$")
    public void taskRowUntouchedByConsumers() {
        ScenarioContext.TaskSnapshot snapshot = ctx.taskSnapshot();
        assertNotNull(snapshot, "Given 步驟該先取快照");
        UUID id = ctx.taskId();
        Map<String, Object> row = db.taskRow(id);
        assertEquals(snapshot.state(), row.get("state"), "state 不變");
        assertEquals(snapshot.eventSeq(), ((Number) row.get("event_seq")).intValue(), "event_seq 不變");
        assertEquals(snapshot.updatedAt(), row.get("updated_at"), "updated_at 不變＝沒有任何 UPDATE");
        assertEquals(snapshot.eventCount(), db.eventTypes(id).size(), "事件數不變");
    }

    @Then("^該任務的四筆事件依 seq 1 到 4 依序到達、分區鍵為 task 加任務編號$")
    public void fourEventsInSeqOrderWithTaskKey() {
        UUID id = ctx.taskId();
        String key = "task:" + id;
        List<ConsumerRecord<String, String>> records = ctx.get("records");
        assertNotNull(records, "When 步驟該先讀 topic");
        List<ConsumerRecord<String, String>> mine = records.stream().filter(r -> key.equals(r.key())).toList();
        assertEquals(4, mine.size(), "created/claimed/started/completed 各一");
        List<Integer> seqs = new ArrayList<>();
        for (ConsumerRecord<String, String> record : mine) {
            JsonNode envelope = objectMapper.readTree(record.value());
            assertEquals(id.toString(), envelope.get("task_id").asText());
            seqs.add(envelope.get("seq").asInt());
        }
        assertEquals(List.of(1, 2, 3, 4), seqs, "同鍵同分區，消費順序＝seq 順序");
        ctx.put("myRecords", mine);
    }

    @Then("^每筆事件 envelope 都帶 tenant_id$")
    public void everyEnvelopeCarriesTenantId() {
        List<ConsumerRecord<String, String>> mine = ctx.get("myRecords");
        assertNotNull(mine, "前一個斷言該先過濾出本任務的事件");
        for (ConsumerRecord<String, String> record : mine) {
            assertEquals(1L, objectMapper.readTree(record.value()).get("tenant_id").asLong(), "事件必帶 tenant_id（GR-003）");
        }
    }

    @Then("^操作被資料庫權限拒絕$")
    public void mutationsDeniedByDbGrants() {
        assertEquals("42501", ctx.get("updateState"), "UPDATE 被權限拒絕（42501）");
        assertEquals("42501", ctx.get("deleteState"), "DELETE 被權限拒絕（42501）");
    }
}
