package com.athena.tms.bdd.steps.taskexecution;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.common.UuidV7;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 任務執行模組的 Given（dsl.md Given 句型區，見
 * specs/truth/features/backend/任務執行/dsl.md）。只建立/確認權威狀態，不含任何斷言。
 */
public class TaskExecutionGivenSteps {

    private final ScenarioWorld world;
    private final TenantFixtures tenantFixtures;
    private final JdbcClient jdbcClient;
    private final DbProbe dbProbe;
    private final MutableClock clock;
    private final BddApiClient apiClient;

    public TaskExecutionGivenSteps(
            ScenarioWorld world,
            TenantFixtures tenantFixtures,
            JdbcClient jdbcClient,
            DbProbe dbProbe,
            MutableClock clock,
            BddApiClient apiClient) {
        this.world = world;
        this.tenantFixtures = tenantFixtures;
        this.jdbcClient = jdbcClient;
        this.dbProbe = dbProbe;
        this.clock = clock;
        this.apiClient = apiClient;
    }

    @假設("打掃 SOP 共有以下項目：")
    public void cleaningSopItemsAre(DataTable table) {
        long tenantId = tenantFixtures.testTenantId();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            int order = Integer.parseInt(requireCell(row, "順序"));
            String label = requireCell(row, "項目");
            UUID id = UuidV7.generate();
            jdbcClient
                    .sql(
                            """
                            INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label, sort_order, active, created_at)
                            VALUES (?, ?, 'cleaning', ?, ?, ?, true, ?)
                            """)
                    .params(id, tenantId, "cleaning-" + order, label, order, OffsetDateTime.now())
                    .update();
        }
    }

    @假設("{string} 已勾選 {string} 的以下 SOP 項：")
    public void staffCheckedSopItems(String staffName, String taskKey, DataTable table) {
        UUID taskId = resolveTaskId(taskKey);
        long tenantId = tenantFixtures.testTenantId();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String label = requireCell(row, "項目");
            UUID sopItemId = sopItemId(tenantId, label);
            Map<String, Object> body = Map.of("sopItemId", sopItemId.toString(), "result", "checked");
            apiClient.post(
                    "/tasks/" + taskId + "/sop-check", BddApiClient.Auth.staff(world.jwt(staffName)), body);
        }
    }

    @假設("{string} 已中斷 {int} 分鐘")
    public void taskInterruptedForMinutes(String taskKey, int minutes) {
        UUID taskId = resolveTaskId(taskKey);
        Instant interruptedAt = lastEventTime(taskId, "interrupted");
        clock.setInstant(interruptedAt.plusSeconds(minutes * 60L));
    }

    /**
     * 直接改寫 task_events／tasks 落地這段歷程，不透過任務引擎——V1 的任務引擎還沒有
     * 「恢復」這個轉移（見 main 的 TaskLifecycleController 把 /resume 固定回 501），
     * 這裡的歷程是測試資料本身要表達的既成事實，不是在練習一次真正的狀態轉移。
     *
     * <p>各段長度等於「分鐘」欄位：第一段寫 started 事件，之後每段依「狀態」欄位寫
     * interrupted（轉為已中斷）或 resumed（轉回進行中）。已結束的進行中段落累計進
     * worked_sec；最後一段若仍是進行中，則維持開放（segment_started_at＝該段開始時刻，
     * 不計入 worked_sec），時間上恰好落在目前 Clock——讓之後呼叫「完成」時，
     * 從 segment_started_at 到當下的這段也能被正確併入。
     */
    @假設("{string} 的進行歷程如下：")
    public void taskTimelineIs(String taskKey, DataTable table) {
        UUID taskId = resolveTaskId(taskKey);
        long tenantId = tenantFixtures.testTenantId();
        Map<String, Object> taskRow = dbProbe
                .taskRow(taskId)
                .orElseThrow(() -> new IllegalStateException("tasks 查無此任務：" + taskId));
        String dept = String.valueOf(taskRow.get("dept"));
        Object assigneeRaw = taskRow.get("assignee_id");
        UUID assigneeId = assigneeRaw == null ? null : UUID.fromString(String.valueOf(assigneeRaw));

        List<Map<String, String>> rows = table.asMaps(String.class, String.class);
        long totalSeconds = rows.stream().mapToLong(r -> Long.parseLong(requireCell(r, "分鐘")) * 60).sum();
        Instant cursor = clock.instant().minusSeconds(totalSeconds);
        Instant firstStart = cursor;

        jdbcClient.sql("DELETE FROM task_events WHERE tenant_id = ? AND task_id = ?").params(tenantId, taskId).update();

        long workedSeconds = 0;
        Instant segmentStartedAt = null;
        String finalStateChinese = null;
        int seq = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String stateChinese = requireCell(row, "狀態");
            long minutes = Long.parseLong(requireCell(row, "分鐘"));
            boolean interrupted = "已中斷".equals(stateChinese);
            String eventType = i == 0 ? "started" : (interrupted ? "interrupted" : "resumed");

            Instant eventTime = cursor;
            seq++;
            insertTaskEvent(tenantId, taskId, seq, eventType, dept, assigneeId, eventTime);

            boolean isLast = i == rows.size() - 1;
            if (isLast) {
                segmentStartedAt = interrupted ? null : eventTime;
            } else if (!interrupted) {
                workedSeconds += minutes * 60;
            }
            cursor = cursor.plusSeconds(minutes * 60);
            finalStateChinese = stateChinese;
        }

        String finalState = Terms.taskState(finalStateChinese);
        jdbcClient
                .sql(
                        """
                        UPDATE tasks
                           SET state = ?,
                               worked_sec = ?,
                               segment_started_at = ?,
                               started_at = ?,
                               event_seq = ?,
                               updated_at = ?
                         WHERE tenant_id = ? AND id = ?
                        """)
                .params(
                        finalState,
                        (int) workedSeconds,
                        segmentStartedAt == null ? null : toOffsetDateTime(segmentStartedAt),
                        toOffsetDateTime(firstStart),
                        seq,
                        toOffsetDateTime(clock.instant()),
                        tenantId,
                        taskId)
                .update();
    }

    private void insertTaskEvent(
            long tenantId, UUID taskId, int seq, String type, String actorRole, UUID actorId, Instant at) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, NULL::jsonb, ?)
                        """)
                .params(tenantId, taskId, seq, type, actorRole, actorId, toOffsetDateTime(at))
                .update();
    }

    private Instant lastEventTime(UUID taskId, String type) {
        return dbProbe
                .queryOne(
                        "SELECT created_at FROM task_events WHERE task_id = ? AND type = ? ORDER BY seq DESC LIMIT 1",
                        taskId, type)
                .map(row -> toInstant(row.get("created_at")))
                .orElseThrow(() -> new IllegalStateException(
                        "task_events 找不到任務「" + taskId + "」的 " + type + " 事件，請先在 Given 建立對應狀態"));
    }

    private UUID sopItemId(long tenantId, String label) {
        return dbProbe
                .queryOne(
                        "SELECT id FROM sop_items WHERE tenant_id = ? AND applies_to = 'cleaning' AND label = ?",
                        tenantId, label)
                .map(row -> (UUID) row.get("id"))
                .orElseThrow(() -> new IllegalStateException(
                        "sop_items 查無標籤「" + label + "」（applies_to=cleaning），請先在 Given 建立"));
    }

    private UUID resolveTaskId(String key) {
        if (key.startsWith("W-")) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        return UUID.fromString(world.taskId(key));
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, clock.getZone());
    }

    private static Instant toInstant(Object v) {
        if (v instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (v instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (v instanceof Instant i) {
            return i;
        }
        throw new IllegalStateException("預期時間型別，實際 " + (v == null ? "null" : v.getClass()));
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
