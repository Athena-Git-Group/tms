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

    @Then(value="^任務 \"([^\"]+)\" 的資料應為:$")
    public void taskDataShouldBe(String symbol, DataTable table) {
        UUID taskId = this.ctx.entityId(symbol);
        this.ctx.taskId(taskId);
        this.assertRowMatches("tasks", taskId, table, "任務 " + symbol);
    }

    @Then(value="^房間 \"([^\"]+)\" 的資料應為:$")
    public void roomDataShouldBe(String symbol, DataTable table) {
        this.assertRowMatches("rooms", this.ctx.entityId(symbol), table, "房間 " + symbol);
    }

    @Then(value="^任務 \"([^\"]+)\" 的 ([a-z_]+) 應為 \"([^\"]*)\"$")
    public void taskColumnShouldBe(String symbol, String column, String expected) {
        UUID taskId = this.ctx.entityId(symbol);
        this.ctx.taskId(taskId);
        this.assertRowMatches("tasks", taskId, Map.of(column, expected), "任務 " + symbol);
    }

    @Then(value="^任務 \"([^\"]+)\" 的 SOP 勾記數應為 (\\d+)$")
    public void taskSopCheckCountShouldBe(String symbol, int expected) {
        UUID taskId = this.ctx.entityId(symbol);
        Integer count = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM task_sop_checks WHERE task_id = ?", Integer.class, new Object[]{taskId});
        Assertions.assertEquals((int)expected, (int)count, (String)("任務 " + symbol + " 的 SOP 勾記數"));
    }

    @Then(value="^任務 \"([^\"]+)\" 已寫入事件 \"([^\"]+)\"$")
    public void taskHasEventType(String symbol, String eventType) {
        UUID taskId = this.ctx.entityId(symbol);
        Assertions.assertTrue((boolean)this.db.eventTypes(taskId).contains(eventType), (String)("任務 " + symbol + " 已寫入事件 " + eventType + ": " + String.valueOf(this.db.eventTypes(taskId))));
    }

    @Then(value="^任務 \"([^\"]+)\" 仍只有 \"([^\"]+)\" 一筆事件$")
    public void taskHasOnlyOneEventOfType(String symbol, String eventType) {
        UUID taskId = this.ctx.entityId(symbol);
        Assertions.assertEquals(List.of(eventType), this.db.eventTypes(taskId), (String)("任務 " + symbol + " 的事件清單"));
    }

    @Then(value="^任務 \"([^\"]+)\" 的事件 \"([^\"]+)\" 的 ([a-z_]+) 應為 \"([^\"]*)\"$")
    public void taskEventFieldShouldBe(String symbol, String eventType, String field, String expected) {
        UUID taskId = this.ctx.entityId(symbol);
        JsonNode payload = this.objectMapper.readTree(this.db.eventPayloadOfType(taskId, eventType));
        Assertions.assertEquals((Object)expected, (Object)payload.get(field).asText(), (String)("任務 " + symbol + " 的事件 " + eventType + " 的 " + field));
    }

    @Then(value="^任務 \"([^\"]+)\" 的事件 \"([^\"]+)\" 的操作者應為「([^」]+)」$")
    public void taskEventActorShouldBe(String symbol, String eventType, String staffName) {
        UUID taskId = this.ctx.entityId(symbol);
        UUID actor = (UUID)this.db.admin().queryForObject("SELECT actor_id FROM task_events WHERE task_id = ? AND type = ?", UUID.class, new Object[]{taskId, eventType});
        UUID expected = (UUID)this.db.admin().queryForObject("SELECT id FROM staff_accounts WHERE tenant_id = 1 AND login_id = ?", UUID.class, new Object[]{this.ctx.staffLogin(staffName)});
        Assertions.assertEquals((Object)expected, (Object)actor, (String)("任務 " + symbol + " 的事件 " + eventType + " 的操作者"));
    }

    @Then(value="^簽發的 guest session 的資料應為:$")
    public void issuedSessionDataShouldBe(DataTable table) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("掃碼該成功: " + last.body()));
        String sessionToken = this.objectMapper.readTree(last.body()).get("sessionToken").asText();
        UUID sessionId = (UUID)this.db.admin().queryForObject("SELECT id FROM guest_sessions WHERE session_token = ?", UUID.class, new Object[]{sessionToken});
        this.assertRowMatches("guest_sessions", sessionId, table, "簽發的 guest session");
    }

    private void assertRowMatches(String tableName, UUID id, DataTable expectedTable, String subject) {
        this.assertRowMatches(tableName, id, (Map)expectedTable.asMaps().getFirst(), subject);
    }

    private void assertRowMatches(String tableName, UUID id, Map<String, String> expectedRow, String subject) {
        Map actualRow = this.db.admin().queryForMap("SELECT " + String.join((CharSequence)", ", expectedRow.keySet()) + " FROM " + tableName + " WHERE id = ?", new Object[]{id});
        expectedRow.forEach((column, rawExpected) -> {
            String expected = this.ctx.resolveCell((String)rawExpected);
            Object actual = actualRow.get(column);
            String label = subject + " 的 " + column;
            if (expected == null) {
                Assertions.assertNull(actual, (String)(label + " 該為 NULL"));
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
