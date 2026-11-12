package com.athena.tms.bdd.steps.push;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.InspectionFixtures;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.OutboxAwait;
import com.athena.tms.bdd.support.PushPayloadView;
import com.athena.tms.bdd.support.StaffFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

public class PushManagerActionSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final LastOperationGuard lastOperationGuard;
    private final OutboxAwait outboxAwait;
    private final MutableClock clock;
    private final TenantFixtures tenantFixtures;
    private final StaffFixtures staffFixtures;
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final InspectionFixtures inspectionFixtures;

    public PushManagerActionSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            LastOperationGuard lastOperationGuard,
            OutboxAwait outboxAwait,
            MutableClock clock,
            TenantFixtures tenantFixtures,
            StaffFixtures staffFixtures,
            JdbcClient jdbcClient,
            ObjectMapper objectMapper,
            InspectionFixtures inspectionFixtures) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.lastOperationGuard = lastOperationGuard;
        this.outboxAwait = outboxAwait;
        this.clock = clock;
        this.tenantFixtures = tenantFixtures;
        this.staffFixtures = staffFixtures;
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.inspectionFixtures = inspectionFixtures;
    }

    @當("{string} 查房 {string}，判定 {string} 不合格")
    public void managerFailsInspectionItem(String managerName, String taskKey, String itemLabel) {
        UUID taskId = UUID.fromString(world.taskId(taskKey));
        lastOperationGuard.captureFor(taskId);

        long tenantId = tenantFixtures.testTenantId();
        tenantFixtures.ensureInspectionSopItems();
        List<Map<String, Object>> sopItems = jdbcClient
                .sql(
                        "SELECT item_key, label FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection' AND active = true ORDER BY sort_order")
                .params(tenantId)
                .query()
                .listOfRows();

        String failedKey = sopItems.stream()
                .filter(row -> itemLabel.equals(String.valueOf(row.get("label"))))
                .map(row -> String.valueOf(row.get("item_key")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "租戶現行啟用的查房項目裡沒有顯示名稱「" + itemLabel + "」的項目"));

        List<Map<String, Object>> items = sopItems.stream()
                .map(row -> Map.<String, Object>of(
                        "key", row.get("item_key"), "pass", !failedKey.equals(row.get("item_key"))))
                .toList();

        ScenarioWorld.ApiResponse response = inspectionFixtures.submit(world, managerName, taskId, items);
        world.setLastInspectionResponse(response);
        world.setLastInspectionItems(items);
    }

    @當("{string} 以原因 {string} 作廢 {string}")
    public void managerVoidsTask(String managerName, String reasonNote, String taskKey) {
        UUID taskId = UUID.fromString(resolveTaskId(taskKey));
        lastOperationGuard.captureFor(taskId);

        Map<String, Object> body = Map.of("reason", "manager_void", "note", reasonNote);
        apiClient.post("/tasks/" + taskId + "/cancel", BddApiClient.Auth.staff(world.jwt(managerName)), body);
    }

    @當("{string} 把 {string} 改派給 {string}")
    public void managerReassignsTask(String managerName, String taskKey, String newAssigneeName) {
        UUID taskId = UUID.fromString(resolveTaskId(taskKey));
        lastOperationGuard.captureFor(taskId);

        Map<String, Object> body = Map.of("assigneeId", world.staffId(newAssigneeName));
        apiClient.post("/tasks/" + taskId + "/reassign", BddApiClient.Auth.staff(world.jwt(managerName)), body);
    }

    @當("{string} 請假")
    public void staffTakesLeave(String staffName) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        long tenantId = tenantFixtures.testTenantId();
        StaffFixtures.CreatedStaff manager =
                staffFixtures.createManagerLikeStaff("系統代登主管-" + UUID.randomUUID(), "manager", "active");

        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        OffsetDateTime fromAt = today.atStartOfDay(clock.getZone()).toOffsetDateTime();
        OffsetDateTime toAt = today.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime();

        Map<String, Object> body = Map.of(
                "tenantId", tenantId,
                "staffId", staffId.toString(),
                "fromAt", fromAt.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "toAt", toAt.withOffsetSameInstant(ZoneOffset.UTC).toString());
        apiClient.post("/leaves", BddApiClient.Auth.staff(manager.jwt()), body);
    }

    @那麼("{string} 只收到一次推播 {string}")
    public void staffReceivedPushExactlyOnce(String staffName, String title) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        outboxAwait.waitForPush(
                v -> staffId.equals(v.recipientStaffId()) && title.equals(v.title()),
                staffName + " 收到標題「" + title + "」的推播");

        long count = dbProbe.outboxRows("push").stream()
                .map(this::parsePush)
                .filter(v -> staffId.equals(v.recipientStaffId()) && title.equals(v.title()))
                .count();
        if (count != 1) {
            throw new AssertionError(
                    staffName + " 預期只收到一次標題「" + title + "」的推播，實際 outbox 有 " + count + " 筆");
        }
    }

    private String resolveTaskId(String taskKey) {
        return taskKey.startsWith("W-") ? world.repairTicketTaskId(taskKey) : world.taskId(taskKey);
    }

    private PushPayloadView parsePush(Map<String, Object> outboxRow) {
        Object payload = outboxRow.get("payload");
        String json = payload instanceof String s ? s : String.valueOf(payload);
        try {
            return objectMapper.readValue(json, PushPayloadView.class);
        } catch (RuntimeException e) {
            throw new AssertionError("解析 push payload 失敗：" + json, e);
        }
    }
}
