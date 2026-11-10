package com.athena.tms.integration.stepdefs.then;

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
import org.junit.jupiter.api.Assertions;
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

    @Then(value="^每次都收到與第一次相同的狀態碼與回應$")
    public void everyResendMatchesFirstResponse() {
        ScenarioContext.HttpResult first = this.ctx.firstResponse();
        Assertions.assertFalse((boolean)this.ctx.replays().isEmpty(), (String)"When 步驟該留下重送結果");
        int nth = 1;
        for (ScenarioContext.HttpResult replay : this.ctx.replays()) {
            Assertions.assertEquals((int)first.status(), (int)replay.status(), (String)("重送第 " + nth + " 次：狀態碼一致"));
            Assertions.assertEquals((Object)first.body(), (Object)replay.body(), (String)("重送第 " + nth + " 次：body 一致"));
            Assertions.assertEquals((Object)"true", (Object)replay.replayedHeader(), (String)"重送走 processed_ops 回放");
            ++nth;
        }
        Assertions.assertEquals((int)1, (int)this.db.processedOpsCount(1L, this.ctx.opId()), (String)"冪等簿有且只有這把鍵");
    }

    @Then(value="^任務只被完成一次、事件只寫一筆、推播只排一筆$")
    public void completedExactlyOnce() {
        UUID id = this.ctx.taskId();
        Assertions.assertEquals((int)1, (int)this.db.eventCountOfType(id, "completed"), (String)"completed 事件只寫一筆");
        Map<String, Object> row = this.db.taskRow(id);
        Assertions.assertEquals((Object)"done", (Object)row.get("state"));
        Assertions.assertEquals((int)4, (int)((Number)row.get("event_seq")).intValue(), (String)"回放不佔 seq");
        Assertions.assertEquals((int)4, (int)this.db.outboxCount("task:" + String.valueOf(id)), (String)"outbox 只排一輪（created..completed 各一，回放後零增量）");
    }

    @Then(value="^該轉移以單一條件式 UPDATE 完成，重複再轉即更新 0 列、該請求應該失敗，回應為 409$")
    public void guardRejectsRepeatWith409() {
        StateConflictException conflict = (StateConflictException)Assertions.assertThrows(StateConflictException.class, () -> Tenants.runAsTenant(1L, () -> this.fixtures.engine().passInspection(1L, this.ctx.taskId(), TestData.MG1)), (String)"同一轉移第二次該被守衛擋下（更新 0 列）");
        Assertions.assertEquals((int)409, (int)conflict.getStatus());
        Assertions.assertEquals((Object)"state_conflict", (Object)conflict.getCode());
    }

    @Then(value="^事件 seq 取自同句 UPDATE 回傳的 event_seq，嚴格遞增且無洞$")
    public void seqComesFromSameStatement() {
        UUID id = this.ctx.taskId();
        Assertions.assertEquals(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4), Integer.valueOf(5)), this.db.eventSeqs(id), (String)"seq 嚴格遞增且無洞");
        Assertions.assertEquals((int)5, (Integer)this.ctx.returnedSeq(), (String)"引擎回傳的 seq＝守衛同句 RETURNING 的 event_seq");
        Assertions.assertEquals((int)5, (int)((Number)this.db.taskRow(id).get("event_seq")).intValue());
    }

    @Then(value="^恰有一次認領成功、另一次請求應該失敗，回應為 409 且原因是 \"state_conflict\"$")
    public void exactlyOneClaimWins() {
        List<String> outcomes = this.ctx.claimOutcomes();
        Assertions.assertEquals((int)1, (int)Collections.frequency(outcomes, "ok"), (String)("恰一次成功: " + String.valueOf(outcomes)));
        Assertions.assertEquals((int)1, (int)Collections.frequency(outcomes, "state_conflict"), (String)("另一次 409: " + String.valueOf(outcomes)));
    }

    @Then(value="^任務停在 \"accepted\"、事件只有 created 與 claimed、輸的那次不佔 seq$")
    public void taskAcceptedWithTwoEvents() {
        UUID id = this.ctx.taskId();
        Map<String, Object> row = this.db.taskRow(id);
        Assertions.assertEquals((Object)"accepted", (Object)row.get("state"));
        Assertions.assertEquals((Object)TestData.STAFF_HK1, (Object)row.get("assignee_id"));
        Assertions.assertEquals((int)2, (int)((Number)row.get("event_seq")).intValue(), (String)"created=1、claimed=2；輸的那次不佔號");
        Assertions.assertEquals(List.of("created", "claimed"), this.db.eventTypes(id), (String)"失敗的轉移不得留下事件");
    }

    @Then(value="^三次請求應該都失敗，回應皆為 409 且原因都是 \"state_conflict\"$")
    public void allThreeRejected() {
        Assertions.assertEquals(List.of(Integer.valueOf(409), Integer.valueOf(409), Integer.valueOf(409)), this.ctx.rejectionStatuses());
        Assertions.assertEquals(List.of("state_conflict", "state_conflict", "state_conflict"), this.ctx.rejectionCodes());
    }

    @Then(value="^被擋的轉移不產生任何事件$")
    public void blockedTransitionsLeaveNoEvents() {
        Assertions.assertEquals(List.of("created", "claimed"), this.db.eventTypes(this.ctx.taskId()), (String)"只有合法的建單與認領留事件");
    }

    @Then(value="^事件 seq 為 1 到 8 嚴格遞增且無洞$")
    public void seqOneToEightNoGaps() {
        Assertions.assertEquals(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4), Integer.valueOf(5), Integer.valueOf(6), Integer.valueOf(7), Integer.valueOf(8)), this.db.eventSeqs(this.ctx.taskId()));
        Assertions.assertEquals((int)8, (Integer)this.ctx.returnedSeq());
    }

    @Then(value="^created 快照之種類為 \"([a-z_]+)\"、來源為 \"([a-z_]+)\"、房號為 \"(\\d+)\"、標題為 \"([^\"]+)\"，且不含訂單鍵$")
    public void createdSnapshotLiteralValues(String type, String source, String roomNo, String title) {
        JsonNode snapshot = this.objectMapper.readTree(this.db.eventPayload(this.ctx.taskId(), 1));
        Assertions.assertEquals((Object)type, (Object)snapshot.get("type").asText(), (String)"種類");
        Assertions.assertEquals((Object)source, (Object)snapshot.get("source").asText(), (String)"來源");
        String roomInDb = (String)this.db.admin().queryForObject("SELECT room_no FROM rooms WHERE id = ?", String.class, new Object[]{UUID.fromString(snapshot.get("room_id").asText())});
        Assertions.assertEquals((Object)roomNo, (Object)roomInDb, (String)"房號");
        Assertions.assertEquals((Object)title, (Object)snapshot.get("title").asText(), (String)"標題");
        Assertions.assertFalse((boolean)snapshot.has("reservation_id"), (String)"沒綁訂單就不落鍵");
    }

    @Then(value="^任務停在 \"inspected\"、redo_count 為 1、之後任何轉移的請求應該失敗，回應為 409$")
    public void terminalInspectedState() {
        UUID id = this.ctx.taskId();
        Map<String, Object> row = this.db.taskRow(id);
        Assertions.assertEquals((Object)"inspected", (Object)row.get("state"));
        Assertions.assertEquals((int)1, (int)((Number)row.get("redo_count")).intValue(), (String)"查房退回一次");
        Assertions.assertNotNull((Object)row.get("done_at"));
        Assertions.assertThrows(StateConflictException.class, () -> Tenants.runAsTenant(1L, () -> this.fixtures.engine().passInspection(1L, id, TestData.MG1)), (String)"inspected 是終點（E-16）");
    }

    @Then(value="^事件依序為 created 與 assigned、event_seq 為 2$")
    public void createdThenAssigned() {
        Assertions.assertEquals(List.of("created", "assigned"), this.db.eventTypes(this.ctx.taskId()), (String)"排房起點＝created(1)＋assigned(2)，spec §5.2 事件形狀");
        Assertions.assertEquals((int)2, (Integer)this.ctx.returnedSeq());
    }

    @Then(value="^任務停在 \"accepted\" 且受派人是「小美」$")
    public void acceptedAssignedToHk1() {
        Map<String, Object> row = this.db.taskRow(this.ctx.taskId());
        Assertions.assertEquals((Object)"accepted", (Object)row.get("state"));
        Assertions.assertEquals((Object)TestData.STAFF_HK1, (Object)row.get("assignee_id"));
    }

    @Then(value="^relay 不送出 \"([^\"]+)\" 那筆，直到同 aggregate_key 的 \"([^\"]+)\" 那筆成功$")
    public void blockedKeyStaysPut(String tailKey, String headKey) {
        Long headId = (Long)this.ctx.get("outbox:" + headKey);
        Long tailId = (Long)this.ctx.get("outbox:" + tailKey);
        Assertions.assertEquals((Object)"pending", (Object)this.db.outboxStatus(headId), (String)"等重試的首筆仍 pending");
        Assertions.assertEquals((Object)"pending", (Object)this.db.outboxStatus(tailId), (String)"同鍵後續列必須跟著等，不得先送");
        Assertions.assertEquals((int)0, (int)this.db.outboxAttempts(tailId), (String)"後續列連試都不該試");
        List<ConsumerRecord<String, String>> records = KafkaProbe.consumeAll("tms.push", Duration.ofSeconds(3L));
        this.ctx.put("pushRecords", records);
        String blockedKey = this.aggregateKeyOf(headId);
        Assertions.assertTrue((boolean)records.stream().noneMatch(r -> blockedKey.equals(r.key())), (String)"被封鎖鍵整串不得外流");
    }

    @Then(value="^其他鍵的事件照常轉送、不被擋$")
    public void otherKeysFlow() {
        List<Long> freeIds = this.ctx.get("outboxFreeIds");
        List<ConsumerRecord<String, String>> records = this.ctx.get("pushRecords");
        Assertions.assertNotNull((Object)records, (String)"前一個斷言該先讀過 push topic");
        for (Long freeId : freeIds) {
            Assertions.assertEquals((Object)"sent", (Object)this.db.outboxStatus(freeId));
            String freeKey = this.aggregateKeyOf(freeId);
            Assertions.assertTrue((boolean)records.stream().anyMatch(r -> freeKey.equals(r.key())), (String)("別鍵事件已上 Kafka: " + freeKey));
        }
    }

    @Then(value="^這筆 pending 事件仍被轉送上 Kafka、不掉單$")
    public void pendingEventEventuallyDelivered() {
        List<Long> freeIds = this.ctx.get("outboxFreeIds");
        Long soloId = freeIds.getFirst();
        Assertions.assertEquals((Object)"sent", (Object)this.db.outboxStatus(soloId));
        String soloKey = this.aggregateKeyOf(soloId);
        List<ConsumerRecord<String, String>> records = KafkaProbe.consumeAll("tms.task-events", Duration.ofSeconds(3L));
        Assertions.assertTrue((boolean)records.stream().anyMatch(r -> soloKey.equals(r.key())), (String)"事件實際到達 Kafka");
    }

    @Then(value="^待送匣 \"([^\"]+)\" 仍是 \"([^\"]+)\"、attempts 仍是 (\\d+)$")
    public void outboxStaysUntouched(String idempotencyKey, String expectedStatus, int expectedAttempts) throws InterruptedException {
        Thread.sleep(3000L);
        Long outboxId = (Long)this.ctx.get("outbox:" + idempotencyKey);
        Assertions.assertEquals((Object)expectedStatus, (Object)this.db.outboxStatus(outboxId), (String)"租約／退避還沒到期，不該被任何 instance 動到");
        Assertions.assertEquals((int)expectedAttempts, (int)this.db.outboxAttempts(outboxId), (String)"沒搶到就不該累加 attempts");
    }

    @Then(value="^待送匣 \"([^\"]+)\" 最終被標記為 \"([^\"]+)\"、attempts 為 (\\d+)$")
    public void outboxReachesStatusWithAttempts(String idempotencyKey, String expectedStatus, int expectedAttempts) {
        Long outboxId = (Long)this.ctx.get("outbox:" + idempotencyKey);
        Awaits.waitUntil(Duration.ofSeconds(20L), "待送匣 " + idempotencyKey + " 該變成 " + expectedStatus, () -> expectedStatus.equals(this.db.outboxStatus(outboxId)));
        Assertions.assertEquals((int)expectedAttempts, (int)this.db.outboxAttempts(outboxId), (String)"attempts 該剛好是這一次 claim 累加的結果");
    }

    @Then(value="^\"([^\"]+)\" 這個鍵在 Kafka 上有 2 筆內容相同的訊息、重複交由下游去重吸收$")
    public void duplicateIdenticalRecordsOnKafka(String idempotencyKey) {
        Long outboxId = (Long)this.ctx.get("outbox:" + idempotencyKey);
        String key = this.aggregateKeyOf(outboxId);
        List<ConsumerRecord<String, String>> records = KafkaProbe.consumeAll("tms.task-events", Duration.ofSeconds(3L));
        List<String> values = records.stream().filter(r -> key.equals(r.key())).map(ConsumerRecord::value).toList();
        Assertions.assertEquals((int)2, (int)values.size(), (String)"重送前模擬的那一筆＋relay 這輪真正送的那一筆，共兩筆");
        Assertions.assertEquals((Object)values.get(0), (Object)values.get(1), (String)"兩筆內容必須一致，下游才能單靠 (task_id, seq) 去重");
    }

    private String aggregateKeyOf(long outboxId) {
        return (String)this.db.admin().queryForObject("SELECT aggregate_key FROM outbox WHERE id = ?", String.class, new Object[]{outboxId});
    }

    @Then(value="^tasks 列未被任何消費者改動，狀態、event_seq、updated_at 均不變$")
    public void taskRowUntouchedByConsumers() {
        ScenarioContext.TaskSnapshot snapshot = this.ctx.taskSnapshot();
        Assertions.assertNotNull((Object)snapshot, (String)"Given 步驟該先取快照");
        UUID id = this.ctx.taskId();
        Map<String, Object> row = this.db.taskRow(id);
        Assertions.assertEquals((Object)snapshot.state(), (Object)row.get("state"), (String)"state 不變");
        Assertions.assertEquals((int)snapshot.eventSeq(), (int)((Number)row.get("event_seq")).intValue(), (String)"event_seq 不變");
        Assertions.assertEquals((Object)snapshot.updatedAt(), (Object)row.get("updated_at"), (String)"updated_at 不變＝沒有任何 UPDATE");
        Assertions.assertEquals((int)snapshot.eventCount(), (int)this.db.eventTypes(id).size(), (String)"事件數不變");
    }

    @Then(value="^該任務的四筆事件依 seq 1 到 4 依序到達、分區鍵為 task 加任務編號$")
    public void fourEventsInSeqOrderWithTaskKey() {
        UUID id = this.ctx.taskId();
        String key = "task:" + String.valueOf(id);
        List<ConsumerRecord<String, String>> records = this.ctx.get("records");
        Assertions.assertNotNull((Object)records, (String)"When 步驟該先讀 topic");
        List<ConsumerRecord<String, String>> mine = records.stream().filter(r -> key.equals(r.key())).toList();
        Assertions.assertEquals((int)4, (int)mine.size(), (String)"created/claimed/started/completed 各一");
        ArrayList<Integer> seqs = new ArrayList<Integer>();
        for (ConsumerRecord record : mine) {
            JsonNode envelope = this.objectMapper.readTree((String)record.value());
            Assertions.assertEquals((Object)id.toString(), (Object)envelope.get("task_id").asText());
            seqs.add(envelope.get("seq").asInt());
        }
        Assertions.assertEquals(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4)), seqs, (String)"同鍵同分區，消費順序＝seq 順序");
        this.ctx.put("myRecords", mine);
    }

    @Then(value="^每筆事件 envelope 都帶 tenant_id$")
    public void everyEnvelopeCarriesTenantId() {
        List<ConsumerRecord<String, String>> mine = this.ctx.get("myRecords");
        Assertions.assertNotNull((Object)mine, (String)"前一個斷言該先過濾出本任務的事件");
        for (ConsumerRecord<String, String> record : mine) {
            Assertions.assertEquals((long)1L, (long)this.objectMapper.readTree((String)record.value()).get("tenant_id").asLong(), (String)"事件必帶 tenant_id（GR-003）");
        }
    }

    @Then(value="^操作被資料庫權限拒絕$")
    public void mutationsDeniedByDbGrants() {
        Assertions.assertEquals((Object)"42501", this.ctx.get("updateState"), (String)"UPDATE 被權限拒絕（42501）");
        Assertions.assertEquals((Object)"42501", this.ctx.get("deleteState"), (String)"DELETE 被權限拒絕（42501）");
    }
}
