package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EntityDataThenSteps {

    private final ScenarioContext ctx;
    private final DbSupport db;
    private final ObjectMapper objectMapper;

    public EntityDataThenSteps(ScenarioContext ctx, DbSupport db, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Then("^任務 \"([^\"]+)\" 的資料應為:$")
    public void taskDataShouldBe(String symbol, DataTable table) {
        UUID taskId = ctx.entityId(symbol);
        ctx.taskId(taskId);
        assertRowMatches("tasks", taskId, table, "任務 " + symbol);
    }

    @Then("^房間 \"([^\"]+)\" 的資料應為:$")
    public void roomDataShouldBe(String symbol, DataTable table) {
        assertRowMatches("rooms", ctx.entityId(symbol), table, "房間 " + symbol);
    }

    /** 單欄版的任務資料斷言：欄位名照資料庫欄名，值是空字串時當 NULL 比對。 */
    @Then("^任務 \"([^\"]+)\" 的 ([a-z_]+) 應為 \"([^\"]*)\"$")
    public void taskColumnShouldBe(String symbol, String column, String expected) {
        UUID taskId = ctx.entityId(symbol);
        ctx.taskId(taskId);
        assertRowMatches("tasks", taskId, Map.of(column, expected), "任務 " + symbol);
    }

    @Then("^任務 \"([^\"]+)\" 的 SOP 勾記數應為 (\\d+)$")
    public void taskSopCheckCountShouldBe(String symbol, int expected) {
        UUID taskId = ctx.entityId(symbol);
        Integer count = db.admin().queryForObject(
                "SELECT count(*) FROM task_sop_checks WHERE task_id = ?", Integer.class, taskId);
        assertEquals(expected, count.intValue(), "任務 " + symbol + " 的 SOP 勾記數");
    }

    @Then("^任務 \"([^\"]+)\" 已寫入事件 \"([^\"]+)\"$")
    public void taskHasEventType(String symbol, String eventType) {
        UUID taskId = ctx.entityId(symbol);
        assertTrue(db.eventTypes(taskId).contains(eventType),
                "任務 " + symbol + " 已寫入事件 " + eventType + ": " + db.eventTypes(taskId));
    }

    @Then("^任務 \"([^\"]+)\" 仍只有 \"([^\"]+)\" 一筆事件$")
    public void taskHasOnlyOneEventOfType(String symbol, String eventType) {
        UUID taskId = ctx.entityId(symbol);
        assertEquals(List.of(eventType), db.eventTypes(taskId), "任務 " + symbol + " 的事件清單");
    }

    @Then("^任務 \"([^\"]+)\" 的事件 \"([^\"]+)\" 的 ([a-z_]+) 應為 \"([^\"]*)\"$")
    public void taskEventFieldShouldBe(String symbol, String eventType, String field, String expected) {
        UUID taskId = ctx.entityId(symbol);
        JsonNode payload = objectMapper.readTree(db.eventPayloadOfType(taskId, eventType));
        assertEquals(expected, payload.get(field).asText(),
                "任務 " + symbol + " 的事件 " + eventType + " 的 " + field);
    }

    @Then("^任務 \"([^\"]+)\" 的事件 \"([^\"]+)\" 的操作者應為「([^」]+)」$")
    public void taskEventActorShouldBe(String symbol, String eventType, String staffName) {
        UUID taskId = ctx.entityId(symbol);
        UUID actor = db.admin().queryForObject(
                "SELECT actor_id FROM task_events WHERE task_id = ? AND type = ?",
                UUID.class, taskId, eventType);
        UUID expected = db.admin().queryForObject(
                "SELECT id FROM staff_accounts WHERE tenant_id = 1 AND login_id = ?",
                UUID.class, ctx.staffLogin(staffName));
        assertEquals(expected, actor, "任務 " + symbol + " 的事件 " + eventType + " 的操作者");
    }

    @Then("^簽發的 guest session 的資料應為:$")
    public void issuedSessionDataShouldBe(DataTable table) {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "掃碼該成功: " + last.body());
        String sessionToken = objectMapper.readTree(last.body()).get("sessionToken").asText();
        UUID sessionId = db.admin().queryForObject(
                "SELECT id FROM guest_sessions WHERE session_token = ?", UUID.class, sessionToken);
        assertRowMatches("guest_sessions", sessionId, table, "簽發的 guest session");
    }

    private void assertRowMatches(String tableName, UUID id, DataTable expectedTable,
                                  String subject) {
        assertRowMatches(tableName, id, expectedTable.asMaps().getFirst(), subject);
    }

    private void assertRowMatches(String tableName, UUID id, Map<String, String> expectedRow,
                                  String subject) {
        // 欄名來自受版控的 feature 檔，不是外部輸入；逐欄拼名查整列
        Map<String, Object> actualRow = db.admin().queryForMap(
                "SELECT " + String.join(", ", expectedRow.keySet())
                        + " FROM " + tableName + " WHERE id = ?", id);
        expectedRow.forEach((column, rawExpected) -> {
            String expected = ctx.resolveCell(rawExpected);
            Object actual = actualRow.get(column);
            String label = subject + " 的 " + column;
            if (expected == null) {
                assertNull(actual, label + " 該為 NULL");
                return;
            }
            assertEquals(normalize(expected), normalize(actual), label);
        });
    }

    /** 時間欄位比瞬間不比字面（DB 回的時區偏移與 feature 寫法常不同形）。 */
    private static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        String text = value.toString();
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException notATimestamp) {
            return text;
        }
    }
}
