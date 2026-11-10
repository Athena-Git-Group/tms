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

    @Then(value="^\u6bcf\u6b21\u90fd\u6536\u5230\u8207\u7b2c\u4e00\u6b21\u76f8\u540c\u7684\u72c0\u614b\u78bc\u8207\u56de\u61c9$")
    public void everyResendMatchesFirstResponse() {
        ScenarioContext.HttpResult first = this.ctx.firstResponse();
        Assertions.assertFalse((boolean)this.ctx.replays().isEmpty(), (String)"When \u6b65\u9a5f\u8a72\u7559\u4e0b\u91cd\u9001\u7d50\u679c");
        int nth = 1;
        for (ScenarioContext.HttpResult replay : this.ctx.replays()) {
            Assertions.assertEquals((int)first.status(), (int)replay.status(), (String)("\u91cd\u9001\u7b2c " + nth + " \u6b21\uff1a\u72c0\u614b\u78bc\u4e00\u81f4"));
            Assertions.assertEquals((Object)first.body(), (Object)replay.body(), (String)("\u91cd\u9001\u7b2c " + nth + " \u6b21\uff1abody \u4e00\u81f4"));
            Assertions.assertEquals((Object)"true", (Object)replay.replayedHeader(), (String)"\u91cd\u9001\u8d70 processed_ops \u56de\u653e");
            ++nth;
        }
        Assertions.assertEquals((int)1, (int)this.db.processedOpsCount(1L, this.ctx.opId()), (String)"\u51aa\u7b49\u7c3f\u6709\u4e14\u53ea\u6709\u9019\u628a\u9375");
    }

    @Then(value="^\u4efb\u52d9\u53ea\u88ab\u5b8c\u6210\u4e00\u6b21\u3001\u4e8b\u4ef6\u53ea\u5beb\u4e00\u7b46\u3001\u63a8\u64ad\u53ea\u6392\u4e00\u7b46$")
    public void completedExactlyOnce() {
        UUID id = this.ctx.taskId();
        Assertions.assertEquals((int)1, (int)this.db.eventCountOfType(id, "completed"), (String)"completed \u4e8b\u4ef6\u53ea\u5beb\u4e00\u7b46");
        Map<String, Object> row = this.db.taskRow(id);
        Assertions.assertEquals((Object)"done", (Object)row.get("state"));
        Assertions.assertEquals((int)4, (int)((Number)row.get("event_seq")).intValue(), (String)"\u56de\u653e\u4e0d\u4f54 seq");
        Assertions.assertEquals((int)4, (int)this.db.outboxCount("task:" + String.valueOf(id)), (String)"outbox \u53ea\u6392\u4e00\u8f2a\uff08created..completed \u5404\u4e00\uff0c\u56de\u653e\u5f8c\u96f6\u589e\u91cf\uff09");
    }

    @Then(value="^\u8a72\u8f49\u79fb\u4ee5\u55ae\u4e00\u689d\u4ef6\u5f0f UPDATE \u5b8c\u6210\uff0c\u91cd\u8907\u518d\u8f49\u5373\u66f4\u65b0 0 \u5217\u3001\u8a72\u8acb\u6c42\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba 409$")
    public void guardRejectsRepeatWith409() {
        StateConflictException conflict = (StateConflictException)Assertions.assertThrows(StateConflictException.class, () -> Tenants.runAsTenant(1L, () -> this.fixtures.engine().passInspection(1L, this.ctx.taskId(), TestData.MG1)), (String)"\u540c\u4e00\u8f49\u79fb\u7b2c\u4e8c\u6b21\u8a72\u88ab\u5b88\u885b\u64cb\u4e0b\uff08\u66f4\u65b0 0 \u5217\uff09");
        Assertions.assertEquals((int)409, (int)conflict.getStatus());
        Assertions.assertEquals((Object)"state_conflict", (Object)conflict.getCode());
    }

    @Then(value="^\u4e8b\u4ef6 seq \u53d6\u81ea\u540c\u53e5 UPDATE \u56de\u50b3\u7684 event_seq\uff0c\u56b4\u683c\u905e\u589e\u4e14\u7121\u6d1e$")
    public void seqComesFromSameStatement() {
        UUID id = this.ctx.taskId();
        Assertions.assertEquals(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4), Integer.valueOf(5)), this.db.eventSeqs(id), (String)"seq \u56b4\u683c\u905e\u589e\u4e14\u7121\u6d1e");
        Assertions.assertEquals((int)5, (Integer)this.ctx.returnedSeq(), (String)"\u5f15\u64ce\u56de\u50b3\u7684 seq\uff1d\u5b88\u885b\u540c\u53e5 RETURNING \u7684 event_seq");
        Assertions.assertEquals((int)5, (int)((Number)this.db.taskRow(id).get("event_seq")).intValue());
    }

    @Then(value="^\u6070\u6709\u4e00\u6b21\u8a8d\u9818\u6210\u529f\u3001\u53e6\u4e00\u6b21\u8acb\u6c42\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba 409 \u4e14\u539f\u56e0\u662f \"state_conflict\"$")
    public void exactlyOneClaimWins() {
        List<String> outcomes = this.ctx.claimOutcomes();
        Assertions.assertEquals((int)1, (int)Collections.frequency(outcomes, "ok"), (String)("\u6070\u4e00\u6b21\u6210\u529f: " + String.valueOf(outcomes)));
        Assertions.assertEquals((int)1, (int)Collections.frequency(outcomes, "state_conflict"), (String)("\u53e6\u4e00\u6b21 409: " + String.valueOf(outcomes)));
    }

    @Then(value="^\u4efb\u52d9\u505c\u5728 \"accepted\"\u3001\u4e8b\u4ef6\u53ea\u6709 created \u8207 claimed\u3001\u8f38\u7684\u90a3\u6b21\u4e0d\u4f54 seq$")
    public void taskAcceptedWithTwoEvents() {
        UUID id = this.ctx.taskId();
        Map<String, Object> row = this.db.taskRow(id);
        Assertions.assertEquals((Object)"accepted", (Object)row.get("state"));
        Assertions.assertEquals((Object)TestData.STAFF_HK1, (Object)row.get("assignee_id"));
        Assertions.assertEquals((int)2, (int)((Number)row.get("event_seq")).intValue(), (String)"created=1\u3001claimed=2\uff1b\u8f38\u7684\u90a3\u6b21\u4e0d\u4f54\u865f");
        Assertions.assertEquals(List.of("created", "claimed"), this.db.eventTypes(id), (String)"\u5931\u6557\u7684\u8f49\u79fb\u4e0d\u5f97\u7559\u4e0b\u4e8b\u4ef6");
    }

    @Then(value="^\u4e09\u6b21\u8acb\u6c42\u61c9\u8a72\u90fd\u5931\u6557\uff0c\u56de\u61c9\u7686\u70ba 409 \u4e14\u539f\u56e0\u90fd\u662f \"state_conflict\"$")
    public void allThreeRejected() {
        Assertions.assertEquals(List.of(Integer.valueOf(409), Integer.valueOf(409), Integer.valueOf(409)), this.ctx.rejectionStatuses());
        Assertions.assertEquals(List.of("state_conflict", "state_conflict", "state_conflict"), this.ctx.rejectionCodes());
    }

    @Then(value="^\u88ab\u64cb\u7684\u8f49\u79fb\u4e0d\u7522\u751f\u4efb\u4f55\u4e8b\u4ef6$")
    public void blockedTransitionsLeaveNoEvents() {
        Assertions.assertEquals(List.of("created", "claimed"), this.db.eventTypes(this.ctx.taskId()), (String)"\u53ea\u6709\u5408\u6cd5\u7684\u5efa\u55ae\u8207\u8a8d\u9818\u7559\u4e8b\u4ef6");
    }

    @Then(value="^\u4e8b\u4ef6 seq \u70ba 1 \u5230 8 \u56b4\u683c\u905e\u589e\u4e14\u7121\u6d1e$")
    public void seqOneToEightNoGaps() {
        Assertions.assertEquals(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4), Integer.valueOf(5), Integer.valueOf(6), Integer.valueOf(7), Integer.valueOf(8)), this.db.eventSeqs(this.ctx.taskId()));
        Assertions.assertEquals((int)8, (Integer)this.ctx.returnedSeq());
    }

    @Then(value="^created \u5feb\u7167\u4e4b\u7a2e\u985e\u70ba \"([a-z_]+)\"\u3001\u4f86\u6e90\u70ba \"([a-z_]+)\"\u3001\u623f\u865f\u70ba \"(\\d+)\"\u3001\u6a19\u984c\u70ba \"([^\"]+)\"\uff0c\u4e14\u4e0d\u542b\u8a02\u55ae\u9375$")
    public void createdSnapshotLiteralValues(String type, String source, String roomNo, String title) {
        JsonNode snapshot = this.objectMapper.readTree(this.db.eventPayload(this.ctx.taskId(), 1));
        Assertions.assertEquals((Object)type, (Object)snapshot.get("type").asText(), (String)"\u7a2e\u985e");
        Assertions.assertEquals((Object)source, (Object)snapshot.get("source").asText(), (String)"\u4f86\u6e90");
        String roomInDb = (String)this.db.admin().queryForObject("SELECT room_no FROM rooms WHERE id = ?", String.class, new Object[]{UUID.fromString(snapshot.get("room_id").asText())});
        Assertions.assertEquals((Object)roomNo, (Object)roomInDb, (String)"\u623f\u865f");
        Assertions.assertEquals((Object)title, (Object)snapshot.get("title").asText(), (String)"\u6a19\u984c");
        Assertions.assertFalse((boolean)snapshot.has("reservation_id"), (String)"\u6c92\u7d81\u8a02\u55ae\u5c31\u4e0d\u843d\u9375");
    }

    @Then(value="^\u4efb\u52d9\u505c\u5728 \"inspected\"\u3001redo_count \u70ba 1\u3001\u4e4b\u5f8c\u4efb\u4f55\u8f49\u79fb\u7684\u8acb\u6c42\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba 409$")
    public void terminalInspectedState() {
        UUID id = this.ctx.taskId();
        Map<String, Object> row = this.db.taskRow(id);
        Assertions.assertEquals((Object)"inspected", (Object)row.get("state"));
        Assertions.assertEquals((int)1, (int)((Number)row.get("redo_count")).intValue(), (String)"\u67e5\u623f\u9000\u56de\u4e00\u6b21");
        Assertions.assertNotNull((Object)row.get("done_at"));
        Assertions.assertThrows(StateConflictException.class, () -> Tenants.runAsTenant(1L, () -> this.fixtures.engine().passInspection(1L, id, TestData.MG1)), (String)"inspected \u662f\u7d42\u9ede\uff08E-16\uff09");
    }

    @Then(value="^\u4e8b\u4ef6\u4f9d\u5e8f\u70ba created \u8207 assigned\u3001event_seq \u70ba 2$")
    public void createdThenAssigned() {
        Assertions.assertEquals(List.of("created", "assigned"), this.db.eventTypes(this.ctx.taskId()), (String)"\u6392\u623f\u8d77\u9ede\uff1dcreated(1)\uff0bassigned(2)\uff0cspec \u00a75.2 \u4e8b\u4ef6\u5f62\u72c0");
        Assertions.assertEquals((int)2, (Integer)this.ctx.returnedSeq());
    }

    @Then(value="^\u4efb\u52d9\u505c\u5728 \"accepted\" \u4e14\u53d7\u6d3e\u4eba\u662f\u300c\u5c0f\u7f8e\u300d$")
    public void acceptedAssignedToHk1() {
        Map<String, Object> row = this.db.taskRow(this.ctx.taskId());
        Assertions.assertEquals((Object)"accepted", (Object)row.get("state"));
        Assertions.assertEquals((Object)TestData.STAFF_HK1, (Object)row.get("assignee_id"));
    }

    @Then(value="^relay \u4e0d\u9001\u51fa \"([^\"]+)\" \u90a3\u7b46\uff0c\u76f4\u5230\u540c aggregate_key \u7684 \"([^\"]+)\" \u90a3\u7b46\u6210\u529f$")
    public void blockedKeyStaysPut(String tailKey, String headKey) {
        Long headId = (Long)this.ctx.get("outbox:" + headKey);
        Long tailId = (Long)this.ctx.get("outbox:" + tailKey);
        Assertions.assertEquals((Object)"pending", (Object)this.db.outboxStatus(headId), (String)"\u7b49\u91cd\u8a66\u7684\u9996\u7b46\u4ecd pending");
        Assertions.assertEquals((Object)"pending", (Object)this.db.outboxStatus(tailId), (String)"\u540c\u9375\u5f8c\u7e8c\u5217\u5fc5\u9808\u8ddf\u8457\u7b49\uff0c\u4e0d\u5f97\u5148\u9001");
        Assertions.assertEquals((int)0, (int)this.db.outboxAttempts(tailId), (String)"\u5f8c\u7e8c\u5217\u9023\u8a66\u90fd\u4e0d\u8a72\u8a66");
        List<ConsumerRecord<String, String>> records = KafkaProbe.consumeAll("tms.push", Duration.ofSeconds(3L));
        this.ctx.put("pushRecords", records);
        String blockedKey = this.aggregateKeyOf(headId);
        Assertions.assertTrue((boolean)records.stream().noneMatch(r -> blockedKey.equals(r.key())), (String)"\u88ab\u5c01\u9396\u9375\u6574\u4e32\u4e0d\u5f97\u5916\u6d41");
    }

    @Then(value="^\u5176\u4ed6\u9375\u7684\u4e8b\u4ef6\u7167\u5e38\u8f49\u9001\u3001\u4e0d\u88ab\u64cb$")
    public void otherKeysFlow() {
        List<Long> freeIds = this.ctx.get("outboxFreeIds");
        List<ConsumerRecord<String, String>> records = this.ctx.get("pushRecords");
        Assertions.assertNotNull((Object)records, (String)"\u524d\u4e00\u500b\u65b7\u8a00\u8a72\u5148\u8b80\u904e push topic");
        for (Long freeId : freeIds) {
            Assertions.assertEquals((Object)"sent", (Object)this.db.outboxStatus(freeId));
            String freeKey = this.aggregateKeyOf(freeId);
            Assertions.assertTrue((boolean)records.stream().anyMatch(r -> freeKey.equals(r.key())), (String)("\u5225\u9375\u4e8b\u4ef6\u5df2\u4e0a Kafka: " + freeKey));
        }
    }

    @Then(value="^\u9019\u7b46 pending \u4e8b\u4ef6\u4ecd\u88ab\u8f49\u9001\u4e0a Kafka\u3001\u4e0d\u6389\u55ae$")
    public void pendingEventEventuallyDelivered() {
        List<Long> freeIds = this.ctx.get("outboxFreeIds");
        Long soloId = freeIds.getFirst();
        Assertions.assertEquals((Object)"sent", (Object)this.db.outboxStatus(soloId));
        String soloKey = this.aggregateKeyOf(soloId);
        List<ConsumerRecord<String, String>> records = KafkaProbe.consumeAll("tms.task-events", Duration.ofSeconds(3L));
        Assertions.assertTrue((boolean)records.stream().anyMatch(r -> soloKey.equals(r.key())), (String)"\u4e8b\u4ef6\u5be6\u969b\u5230\u9054 Kafka");
    }

    @Then(value="^\u5f85\u9001\u5323 \"([^\"]+)\" \u4ecd\u662f \"([^\"]+)\"\u3001attempts \u4ecd\u662f (\\d+)$")
    public void outboxStaysUntouched(String idempotencyKey, String expectedStatus, int expectedAttempts) throws InterruptedException {
        Thread.sleep(3000L);
        Long outboxId = (Long)this.ctx.get("outbox:" + idempotencyKey);
        Assertions.assertEquals((Object)expectedStatus, (Object)this.db.outboxStatus(outboxId), (String)"\u79df\u7d04\uff0f\u9000\u907f\u9084\u6c92\u5230\u671f\uff0c\u4e0d\u8a72\u88ab\u4efb\u4f55 instance \u52d5\u5230");
        Assertions.assertEquals((int)expectedAttempts, (int)this.db.outboxAttempts(outboxId), (String)"\u6c92\u6436\u5230\u5c31\u4e0d\u8a72\u7d2f\u52a0 attempts");
    }

    @Then(value="^\u5f85\u9001\u5323 \"([^\"]+)\" \u6700\u7d42\u88ab\u6a19\u8a18\u70ba \"([^\"]+)\"\u3001attempts \u70ba (\\d+)$")
    public void outboxReachesStatusWithAttempts(String idempotencyKey, String expectedStatus, int expectedAttempts) {
        Long outboxId = (Long)this.ctx.get("outbox:" + idempotencyKey);
        Awaits.waitUntil(Duration.ofSeconds(20L), "\u5f85\u9001\u5323 " + idempotencyKey + " \u8a72\u8b8a\u6210 " + expectedStatus, () -> expectedStatus.equals(this.db.outboxStatus(outboxId)));
        Assertions.assertEquals((int)expectedAttempts, (int)this.db.outboxAttempts(outboxId), (String)"attempts \u8a72\u525b\u597d\u662f\u9019\u4e00\u6b21 claim \u7d2f\u52a0\u7684\u7d50\u679c");
    }

    @Then(value="^\"([^\"]+)\" \u9019\u500b\u9375\u5728 Kafka \u4e0a\u6709 2 \u7b46\u5167\u5bb9\u76f8\u540c\u7684\u8a0a\u606f\u3001\u91cd\u8907\u4ea4\u7531\u4e0b\u6e38\u53bb\u91cd\u5438\u6536$")
    public void duplicateIdenticalRecordsOnKafka(String idempotencyKey) {
        Long outboxId = (Long)this.ctx.get("outbox:" + idempotencyKey);
        String key = this.aggregateKeyOf(outboxId);
        List<ConsumerRecord<String, String>> records = KafkaProbe.consumeAll("tms.task-events", Duration.ofSeconds(3L));
        List<String> values = records.stream().filter(r -> key.equals(r.key())).map(ConsumerRecord::value).toList();
        Assertions.assertEquals((int)2, (int)values.size(), (String)"\u91cd\u9001\u524d\u6a21\u64ec\u7684\u90a3\u4e00\u7b46\uff0brelay \u9019\u8f2a\u771f\u6b63\u9001\u7684\u90a3\u4e00\u7b46\uff0c\u5171\u5169\u7b46");
        Assertions.assertEquals((Object)values.get(0), (Object)values.get(1), (String)"\u5169\u7b46\u5167\u5bb9\u5fc5\u9808\u4e00\u81f4\uff0c\u4e0b\u6e38\u624d\u80fd\u55ae\u9760 (task_id, seq) \u53bb\u91cd");
    }

    private String aggregateKeyOf(long outboxId) {
        return (String)this.db.admin().queryForObject("SELECT aggregate_key FROM outbox WHERE id = ?", String.class, new Object[]{outboxId});
    }

    @Then(value="^tasks \u5217\u672a\u88ab\u4efb\u4f55\u6d88\u8cbb\u8005\u6539\u52d5\uff0c\u72c0\u614b\u3001event_seq\u3001updated_at \u5747\u4e0d\u8b8a$")
    public void taskRowUntouchedByConsumers() {
        ScenarioContext.TaskSnapshot snapshot = this.ctx.taskSnapshot();
        Assertions.assertNotNull((Object)snapshot, (String)"Given \u6b65\u9a5f\u8a72\u5148\u53d6\u5feb\u7167");
        UUID id = this.ctx.taskId();
        Map<String, Object> row = this.db.taskRow(id);
        Assertions.assertEquals((Object)snapshot.state(), (Object)row.get("state"), (String)"state \u4e0d\u8b8a");
        Assertions.assertEquals((int)snapshot.eventSeq(), (int)((Number)row.get("event_seq")).intValue(), (String)"event_seq \u4e0d\u8b8a");
        Assertions.assertEquals((Object)snapshot.updatedAt(), (Object)row.get("updated_at"), (String)"updated_at \u4e0d\u8b8a\uff1d\u6c92\u6709\u4efb\u4f55 UPDATE");
        Assertions.assertEquals((int)snapshot.eventCount(), (int)this.db.eventTypes(id).size(), (String)"\u4e8b\u4ef6\u6578\u4e0d\u8b8a");
    }

    @Then(value="^\u8a72\u4efb\u52d9\u7684\u56db\u7b46\u4e8b\u4ef6\u4f9d seq 1 \u5230 4 \u4f9d\u5e8f\u5230\u9054\u3001\u5206\u5340\u9375\u70ba task \u52a0\u4efb\u52d9\u7de8\u865f$")
    public void fourEventsInSeqOrderWithTaskKey() {
        UUID id = this.ctx.taskId();
        String key = "task:" + String.valueOf(id);
        List<ConsumerRecord<String, String>> records = this.ctx.get("records");
        Assertions.assertNotNull((Object)records, (String)"When \u6b65\u9a5f\u8a72\u5148\u8b80 topic");
        List<ConsumerRecord<String, String>> mine = records.stream().filter(r -> key.equals(r.key())).toList();
        Assertions.assertEquals((int)4, (int)mine.size(), (String)"created/claimed/started/completed \u5404\u4e00");
        ArrayList<Integer> seqs = new ArrayList<Integer>();
        for (ConsumerRecord record : mine) {
            JsonNode envelope = this.objectMapper.readTree((String)record.value());
            Assertions.assertEquals((Object)id.toString(), (Object)envelope.get("task_id").asText());
            seqs.add(envelope.get("seq").asInt());
        }
        Assertions.assertEquals(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4)), seqs, (String)"\u540c\u9375\u540c\u5206\u5340\uff0c\u6d88\u8cbb\u9806\u5e8f\uff1dseq \u9806\u5e8f");
        this.ctx.put("myRecords", mine);
    }

    @Then(value="^\u6bcf\u7b46\u4e8b\u4ef6 envelope \u90fd\u5e36 tenant_id$")
    public void everyEnvelopeCarriesTenantId() {
        List<ConsumerRecord<String, String>> mine = this.ctx.get("myRecords");
        Assertions.assertNotNull((Object)mine, (String)"\u524d\u4e00\u500b\u65b7\u8a00\u8a72\u5148\u904e\u6ffe\u51fa\u672c\u4efb\u52d9\u7684\u4e8b\u4ef6");
        for (ConsumerRecord<String, String> record : mine) {
            Assertions.assertEquals((long)1L, (long)this.objectMapper.readTree((String)record.value()).get("tenant_id").asLong(), (String)"\u4e8b\u4ef6\u5fc5\u5e36 tenant_id\uff08GR-003\uff09");
        }
    }

    @Then(value="^\u64cd\u4f5c\u88ab\u8cc7\u6599\u5eab\u6b0a\u9650\u62d2\u7d55$")
    public void mutationsDeniedByDbGrants() {
        Assertions.assertEquals((Object)"42501", this.ctx.get("updateState"), (String)"UPDATE \u88ab\u6b0a\u9650\u62d2\u7d55\uff0842501\uff09");
        Assertions.assertEquals((Object)"42501", this.ctx.get("deleteState"), (String)"DELETE \u88ab\u6b0a\u9650\u62d2\u7d55\uff0842501\uff09");
    }
}
