package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class EntityDataThenSteps {
    private final ScenarioContext ctx;
    private final DbSupport db;
    private final ObjectMapper objectMapper;

    public EntityDataThenSteps(ScenarioContext ctx, DbSupport db, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Then(value="^\u4efb\u52d9 \"([^\"]+)\" \u7684\u8cc7\u6599\u61c9\u70ba:$")
    public void taskDataShouldBe(String symbol, DataTable table) {
        UUID taskId = this.ctx.entityId(symbol);
        this.ctx.taskId(taskId);
        this.assertRowMatches("tasks", taskId, table, "\u4efb\u52d9 " + symbol);
    }

    @Then(value="^\u623f\u9593 \"([^\"]+)\" \u7684\u8cc7\u6599\u61c9\u70ba:$")
    public void roomDataShouldBe(String symbol, DataTable table) {
        this.assertRowMatches("rooms", this.ctx.entityId(symbol), table, "\u623f\u9593 " + symbol);
    }

    @Then(value="^\u4efb\u52d9 \"([^\"]+)\" \u7684 ([a-z_]+) \u61c9\u70ba \"([^\"]*)\"$")
    public void taskColumnShouldBe(String symbol, String column, String expected) {
        UUID taskId = this.ctx.entityId(symbol);
        this.ctx.taskId(taskId);
        this.assertRowMatches("tasks", taskId, Map.of(column, expected), "\u4efb\u52d9 " + symbol);
    }

    @Then(value="^\u4efb\u52d9 \"([^\"]+)\" \u7684 SOP \u52fe\u8a18\u6578\u61c9\u70ba (\\d+)$")
    public void taskSopCheckCountShouldBe(String symbol, int expected) {
        UUID taskId = this.ctx.entityId(symbol);
        Integer count = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM task_sop_checks WHERE task_id = ?", Integer.class, new Object[]{taskId});
        Assertions.assertEquals((int)expected, (int)count, (String)("\u4efb\u52d9 " + symbol + " \u7684 SOP \u52fe\u8a18\u6578"));
    }

    @Then(value="^\u4efb\u52d9 \"([^\"]+)\" \u5df2\u5beb\u5165\u4e8b\u4ef6 \"([^\"]+)\"$")
    public void taskHasEventType(String symbol, String eventType) {
        UUID taskId = this.ctx.entityId(symbol);
        Assertions.assertTrue((boolean)this.db.eventTypes(taskId).contains(eventType), (String)("\u4efb\u52d9 " + symbol + " \u5df2\u5beb\u5165\u4e8b\u4ef6 " + eventType + ": " + String.valueOf(this.db.eventTypes(taskId))));
    }

    @Then(value="^\u4efb\u52d9 \"([^\"]+)\" \u4ecd\u53ea\u6709 \"([^\"]+)\" \u4e00\u7b46\u4e8b\u4ef6$")
    public void taskHasOnlyOneEventOfType(String symbol, String eventType) {
        UUID taskId = this.ctx.entityId(symbol);
        Assertions.assertEquals(List.of(eventType), this.db.eventTypes(taskId), (String)("\u4efb\u52d9 " + symbol + " \u7684\u4e8b\u4ef6\u6e05\u55ae"));
    }

    @Then(value="^\u4efb\u52d9 \"([^\"]+)\" \u7684\u4e8b\u4ef6 \"([^\"]+)\" \u7684 ([a-z_]+) \u61c9\u70ba \"([^\"]*)\"$")
    public void taskEventFieldShouldBe(String symbol, String eventType, String field, String expected) {
        UUID taskId = this.ctx.entityId(symbol);
        JsonNode payload = this.objectMapper.readTree(this.db.eventPayloadOfType(taskId, eventType));
        Assertions.assertEquals((Object)expected, (Object)payload.get(field).asText(), (String)("\u4efb\u52d9 " + symbol + " \u7684\u4e8b\u4ef6 " + eventType + " \u7684 " + field));
    }

    @Then(value="^\u4efb\u52d9 \"([^\"]+)\" \u7684\u4e8b\u4ef6 \"([^\"]+)\" \u7684\u64cd\u4f5c\u8005\u61c9\u70ba\u300c([^\u300d]+)\u300d$")
    public void taskEventActorShouldBe(String symbol, String eventType, String staffName) {
        UUID taskId = this.ctx.entityId(symbol);
        UUID actor = (UUID)this.db.admin().queryForObject("SELECT actor_id FROM task_events WHERE task_id = ? AND type = ?", UUID.class, new Object[]{taskId, eventType});
        UUID expected = (UUID)this.db.admin().queryForObject("SELECT id FROM staff_accounts WHERE tenant_id = 1 AND login_id = ?", UUID.class, new Object[]{this.ctx.staffLogin(staffName)});
        Assertions.assertEquals((Object)expected, (Object)actor, (String)("\u4efb\u52d9 " + symbol + " \u7684\u4e8b\u4ef6 " + eventType + " \u7684\u64cd\u4f5c\u8005"));
    }

    @Then(value="^\u7c3d\u767c\u7684 guest session \u7684\u8cc7\u6599\u61c9\u70ba:$")
    public void issuedSessionDataShouldBe(DataTable table) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u6383\u78bc\u8a72\u6210\u529f: " + last.body()));
        String sessionToken = this.objectMapper.readTree(last.body()).get("sessionToken").asText();
        UUID sessionId = (UUID)this.db.admin().queryForObject("SELECT id FROM guest_sessions WHERE session_token = ?", UUID.class, new Object[]{sessionToken});
        this.assertRowMatches("guest_sessions", sessionId, table, "\u7c3d\u767c\u7684 guest session");
    }

    private void assertRowMatches(String tableName, UUID id, DataTable expectedTable, String subject) {
        this.assertRowMatches(tableName, id, (Map)expectedTable.asMaps().getFirst(), subject);
    }

    private void assertRowMatches(String tableName, UUID id, Map<String, String> expectedRow, String subject) {
        Map actualRow = this.db.admin().queryForMap("SELECT " + String.join((CharSequence)", ", expectedRow.keySet()) + " FROM " + tableName + " WHERE id = ?", new Object[]{id});
        expectedRow.forEach((column, rawExpected) -> {
            String expected = this.ctx.resolveCell((String)rawExpected);
            Object actual = actualRow.get(column);
            String label = subject + " \u7684 " + column;
            if (expected == null) {
                Assertions.assertNull(actual, (String)(label + " \u8a72\u70ba NULL"));
                return;
            }
            Assertions.assertEquals((Object)EntityDataThenSteps.normalize(expected), (Object)EntityDataThenSteps.normalize(actual), (String)label);
        });
    }

    private static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            Timestamp timestamp = (Timestamp)value;
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime) {
            OffsetDateTime offsetDateTime = (OffsetDateTime)value;
            return offsetDateTime.toInstant();
        }
        String text = value.toString();
        try {
            return OffsetDateTime.parse(text).toInstant();
        }
        catch (DateTimeParseException notATimestamp) {
            return text;
        }
    }
}
