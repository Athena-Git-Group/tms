package com.athena.tms.bdd.steps.push;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
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

/**
 * 推播模組（dsl.md: specs/truth/features/backend/推播/dsl.md）主管／員工動作觸發的
 * 句型：查房退回、作廢、改派、請假，以及「只收到一次推播」的去重檢查。共用句型
 * （收到推播／收到以下推播／沒有收到任何推播）在 steps/common，本類別不重複定義。
 */
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
            ObjectMapper objectMapper) {
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
    }

    /**
     * <p>createInspections 的 items 鍵集必須是租戶查房項目全集（缺項 422），所以這裡先
     * 查出全部查房項目，只把 Gherkin 點名的那一項判為不合格，其餘判為合格。
     */
    @當("{string} 查房 {string}，判定 {string} 不合格")
    public void managerFailsInspectionItem(String managerName, String taskKey, String itemLabel) {
        UUID taskId = UUID.fromString(world.taskId(taskKey));
        lastOperationGuard.captureFor(taskId);

        long tenantId = tenantFixtures.testTenantId();
        // V2 seed 只替示範租戶（id=1）種查房項目，測試租戶的查房項目要由 fixture 自己落地。
        tenantFixtures.ensureInspectionSopItems();
        List<Map<String, Object>> sopItems = jdbcClient
                .sql(
                        "SELECT item_key, label FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection' AND active = true ORDER BY sort_order")
                .params(tenantId)
                .query()
                .listOfRows();

        String failedKey = sopItems.stream()
                .filter(row -> String.valueOf(row.get("label")).contains(itemLabel))
                .map(row -> String.valueOf(row.get("item_key")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "租戶查房項目裡找不到標籤包含「" + itemLabel + "」的項目"));

        List<Map<String, Object>> items = sopItems.stream()
                .map(row -> Map.<String, Object>of(
                        "key", row.get("item_key"), "pass", !failedKey.equals(row.get("item_key"))))
                .toList();

        Map<String, Object> body = Map.of(
                "tenantId", tenantId,
                "taskId", taskId.toString(),
                "inspectorId", world.staffId(managerName),
                "result", "failed",
                "items", items);
        apiClient.post("/inspections", BddApiClient.Auth.staff(world.jwt(managerName)), body);
    }

    @當("{string} 以原因 {string} 作廢 {string}")
    public void managerVoidsTask(String managerName, String reasonNote, String taskKey) {
        UUID taskId = UUID.fromString(resolveTaskId(taskKey));
        lastOperationGuard.captureFor(taskId);

        Map<String, Object> body = Map.of("reason", "manager_void", "note", reasonNote);
        apiClient.post("/tasks/" + taskId + "/cancel", BddApiClient.Auth.staff(world.jwt(managerName)), body);
    }

    /**
     * <p>contracts 的 tasksReassign requestBody 掛的是整列 tasksRequest schema，但這個
     * 動作實際只改一個欄位；送整列任務資料反而要求呼叫端重複提供這個動作不該變動的
     * type／dept／state 等欄位，所以這裡只送改派這個動作本身需要的 assigneeId。
     *
     * <p>dsl.md 的任務參數：房號，修繕任務（工務單）用單號。W- 開頭視為工務單單號，
     * 透過「已有以下工務單：」登記的單號→task id 對照解析；其餘視為房號走任務對照。
     */
    @當("{string} 把 {string} 改派給 {string}")
    public void managerReassignsTask(String managerName, String taskKey, String newAssigneeName) {
        UUID taskId = UUID.fromString(resolveTaskId(taskKey));
        lastOperationGuard.captureFor(taskId);

        Map<String, Object> body = Map.of("assigneeId", world.staffId(newAssigneeName));
        apiClient.post("/tasks/" + taskId + "/reassign", BddApiClient.Auth.staff(world.jwt(managerName)), body);
    }

    /**
     * <p>dsl.md 只給請假員工本人，沒有指定哪位主管登記；身分只是用來滿足
     * createLeaves 的認證（API 本身認誰登記的主管不是這句要驗證的對象），
     * 所以現場建一個不對外曝光姓名的主管帳號。
     */
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

    /** dsl.md 必查：權威狀態——outbox 中該員工、該標題的 kind push 資料剛好一筆，重複掃描不新增。 */
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

    /** 任務參數解析：W- 開頭的是工務單單號（查工務單對照），其餘是房號（查任務對照）。 */
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
