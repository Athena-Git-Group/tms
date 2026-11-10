package com.athena.tms.bdd.steps.shiftend;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.JobTrigger;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.PushPayloadView;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.JwtService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 班末掃描模組的 stepdef（dsl.md: specs/truth/features/backend/班末掃描/dsl.md）。
 * 只落地本模組專屬的 When／Then 句型，共用句型見 steps/common。
 */
public class ShiftEndSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final LastOperationGuard lastOperationGuard;
    private final JobTrigger jobTrigger;
    private final JwtService jwtService;
    private final TenantFixtures tenantFixtures;
    private final ObjectMapper objectMapper;

    public ShiftEndSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            LastOperationGuard lastOperationGuard,
            JobTrigger jobTrigger,
            JwtService jwtService,
            TenantFixtures tenantFixtures,
            ObjectMapper objectMapper) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.lastOperationGuard = lastOperationGuard;
        this.jobTrigger = jobTrigger;
        this.jwtService = jwtService;
        this.tenantFixtures = tenantFixtures;
        this.objectMapper = objectMapper;
    }

    @當("班末掃描執行")
    public void shiftEndScanRuns() {
        jobTrigger.run("shift-end-scan");
    }

    /**
     * dsl.md 怎麼做：以主管身分呼叫 tasksRequeue（POST /tasks/{id}/requeue），附交代。
     * 交代對應 contracts tasksRequest 的 note 欄位（note：住客原文/翻譯/交代/當場回答）。
     */
    @當("{string} 以交代 {string} 把 {string} 退回待認領")
    public void managerRequeuesTaskWithHandoverNote(String managerName, String handoverNote, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        Map<String, Object> body = Map.of("note", handoverNote);
        apiClient.post("/tasks/" + taskId + "/requeue", BddApiClient.Auth.staff(world.jwt(managerName)), body);
    }

    /**
     * dsl.md 必查：權威狀態——每一列都有 dept housekeeping、狀態 pool 的任務，
     * 並且有給的欄位都相符；跨視角——房務員呼叫 listDeptPool（GET /pool）時看得到這些單。
     */
    @那麼("房務待認領包含以下任務：")
    public void poolContainsTasks(DataTable table) {
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String roomNo = requireCell(row, "房號");
            UUID taskId = resolveTaskId(roomNo);
            Map<String, Object> taskRow = requireTaskRow(taskId);

            check(taskId, "tasks.dept", "housekeeping", taskRow.get("dept"));
            check(taskId, "tasks.state", "pool", taskRow.get("state"));

            String content = blankToNull(row.get("內容"));
            if (content != null) {
                check(taskId, "tasks.title", content, taskRow.get("title"));
            }
            String sourceChinese = blankToNull(row.get("來源"));
            if (sourceChinese != null) {
                check(taskId, "tasks.pool_source", Terms.poolSource(sourceChinese), taskRow.get("pool_source"));
            }
            String mark = blankToNull(row.get("標記"));
            if (mark != null) {
                boolean expectedPriorityNotify = "優先通知".equals(mark);
                check(taskId, "tasks.priority_notify", expectedPriorityNotify, taskRow.get("priority_notify"));
            }
            String handoverNote = blankToNull(row.get("交代"));
            if (handoverNote != null) {
                check(taskId, "tasks.requeue_note", handoverNote, taskRow.get("requeue_note"));
            }

            assertVisibleInDeptPool(taskId);
        }
    }

    /**
     * dsl.md 必查：不該發生——outbox 沒有任何收件人是該員工、內容帶該任務 id 或該房號的
     * kind push 資料列。{@code 任務} 參數在本模組固定是房號，直接兼用來比對推播文字內容。
     */
    @那麼("{string} 沒有收到關於 {string} 的推播")
    public void staffReceivedNoPushAboutTask(String staffName, String taskKey) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        UUID taskId = resolveTaskId(taskKey);

        boolean found = dbProbe.outboxRows("push").stream()
                .map(this::parsePush)
                .anyMatch(v -> staffId.equals(v.recipientStaffId()) && referencesTask(v, taskId, taskKey));
        if (found) {
            throw new AssertionError(
                    staffName + " 預期沒有收到關於「" + taskKey + "」的推播，但 outbox 裡找到至少一筆相符的 push 資料列");
        }
    }

    private boolean referencesTask(PushPayloadView push, UUID taskId, String roomNo) {
        PushPayloadView.Target target = push.target();
        if (target != null && (taskId.equals(target.taskId()) || taskId.equals(target.highlightTaskId()))) {
            return true;
        }
        String title = push.title();
        String body = push.body();
        return (title != null && title.contains(roomNo)) || (body != null && body.contains(roomNo));
    }

    private void assertVisibleInDeptPool(UUID taskId) {
        String jwt = mintHousekeepingStaffJwt();
        ScenarioWorld.ApiResponse resp = apiClient.get("/pool", BddApiClient.Auth.staff(jwt));
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError("listDeptPool 預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        JsonNode items = resp.json().path("items");
        boolean visible = false;
        if (items.isArray()) {
            for (JsonNode item : items) {
                String itemTaskId = item.path("taskId").asText(null);
                if (taskId.toString().equalsIgnoreCase(itemTaskId)) {
                    visible = true;
                    break;
                }
            }
        }
        if (!visible) {
            throw new AssertionError("房務員呼叫 listDeptPool 看不到任務 id=" + taskId + "（items=" + items + "）");
        }
    }

    /** 跨視角驗證要用「DB 裡目前在職的房務員」，不限定是哪個 Given 建的員工。 */
    private String mintHousekeepingStaffJwt() {
        long tenantId = tenantFixtures.testTenantId();
        Map<String, Object> staff = dbProbe
                .queryOne(
                        "SELECT id, role, dept FROM staff_accounts WHERE tenant_id = ? AND dept = 'housekeeping' AND active = true LIMIT 1",
                        tenantId)
                .orElseThrow(() -> new IllegalStateException("staff_accounts 查無在職房務員，無法跨視角驗證 listDeptPool"));
        UUID staffId = UUID.fromString(String.valueOf(staff.get("id")));
        return jwtService.issue(tenantId, staffId, (String) staff.get("role"), (String) staff.get("dept")).token();
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

    private UUID resolveTaskId(String key) {
        if (key.startsWith("W-")) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        return UUID.fromString(world.taskId(key));
    }

    private Map<String, Object> requireTaskRow(UUID taskId) {
        return dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此任務：" + taskId));
    }

    private static void check(UUID taskId, String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual + "（taskId=" + taskId + "）");
        }
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
