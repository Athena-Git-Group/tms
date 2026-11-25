package com.athena.tms.bdd.steps.repair;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public class RepairVerificationWaivedSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final DeskApi deskApi;
    private final LastOperationGuard lastOperationGuard;
    private final TenantFixtures tenantFixtures;

    public RepairVerificationWaivedSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            DeskApi deskApi,
            LastOperationGuard lastOperationGuard,
            TenantFixtures tenantFixtures) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.deskApi = deskApi;
        this.lastOperationGuard = lastOperationGuard;
        this.tenantFixtures = tenantFixtures;
    }

    @那麼("{string} 免驗收結案")
    public void closedWithoutVerification(String ticketNo) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        Map<String, Object> task = dbProbe.taskRow(taskId)
                .orElseThrow(() -> new AssertionError("tasks 查無工務單 " + ticketNo));
        check(ticketNo + " tasks.state", "closed", task.get("state"));
        if (task.get("ended_at") == null) {
            throw new AssertionError(ticketNo + " tasks.ended_at 預期已寫入，實際 NULL");
        }

        Map<String, Object> ticket = dbProbe
                .queryOne("SELECT verified_by, verified_at FROM repair_tickets WHERE task_id = ?", taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無 " + ticketNo));
        if (ticket.get("verified_by") != null || ticket.get("verified_at") != null) {
            throw new AssertionError(ticketNo + " 沒有人驗收，verified_by／verified_at 應為空，實際 "
                    + ticket.get("verified_by") + "／" + ticket.get("verified_at"));
        }

        long verifiedEvents = dbProbe.count(
                "SELECT count(*) FROM task_events WHERE task_id = ? AND type = 'repair_verified'", taskId);
        check(ticketNo + " 的 repair_verified 事件筆數", 0L, verifiedEvents);
        long waivedCompletions = dbProbe.count(
                """
                SELECT count(*) FROM task_events
                 WHERE task_id = ? AND type = 'completed' AND payload ->> 'verificationWaived' = 'true'
                """,
                taskId);
        check(ticketNo + " 帶 verificationWaived=true 的 completed 事件筆數", 1L, waivedCompletions);

        ScenarioWorld.ApiResponse detail =
                apiClient.get("/desk/tasks/" + taskId, BddApiClient.Auth.staff(world.jwt(deskReaderName())));
        JsonNode json = detail.json();
        if (detail.status() != 200 || json == null) {
            throw new AssertionError("readDeskTask 預期 200，實際 status=" + detail.status() + "，body=" + detail.body());
        }
        check(ticketNo + " 櫃檯詳情 state", "closed", json.path("state").asText(null));
        if (json.path("endedAt").isNull() || json.path("endedAt").isMissingNode()) {
            throw new AssertionError(ticketNo + " 櫃檯詳情 endedAt 預期有值，實際 body=" + detail.body());
        }
    }

    @那麼("{string} 在 {string} 的待驗收清單")
    public void inVerifyQueue(String ticketNo, String managerName) {
        if (!verifyQueueTicketNos(managerName).contains(ticketNo)) {
            throw new AssertionError(managerName + " 的待驗收清單預期有 " + ticketNo + "，實際 " + verifyQueueTicketNos(managerName));
        }
    }

    @那麼("{string} 不在 {string} 的待驗收清單")
    public void notInVerifyQueue(String ticketNo, String managerName) {
        List<String> ticketNos = verifyQueueTicketNos(managerName);
        if (ticketNos.contains(ticketNo)) {
            throw new AssertionError(managerName + " 的待驗收清單不應有 " + ticketNo + "，實際 " + ticketNos);
        }
    }

    @那麼("{string} 的住客讀到這張需求已結束")
    public void guestReadsRequestEnded(String roomNo) {
        JsonNode request = guestRequest(roomNo);
        JsonNode endedAt = request.path("endedAt");
        if (endedAt.isMissingNode() || endedAt.isNull()) {
            throw new AssertionError(roomNo + " 的住客讀到的需求 endedAt 預期有值，實際 " + request);
        }
        requireTaskEnded(roomNo, true);
    }

    @那麼("{string} 的住客讀到這張需求還沒結束")
    public void guestReadsRequestNotEnded(String roomNo) {
        JsonNode request = guestRequest(roomNo);
        JsonNode endedAt = request.path("endedAt");
        if (endedAt.isMissingNode() || !endedAt.isNull()) {
            throw new AssertionError(roomNo + " 的住客讀到的需求預期帶 endedAt=null，實際 " + request);
        }
        requireTaskEnded(roomNo, false);
    }

    @當("{string} 的住客為這張需求評 {int} 星")
    public void guestRatesRequest(String roomNo, int stars) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        lastOperationGuard.captureFor(taskId);
        apiClient.postUnversioned("/guest/requests/" + taskId + "/rating", guestAuth(roomNo), Map.of("rating", stars));
    }

    @那麼("{string} 的住客讀到這張需求的評分為 {int}")
    public void guestReadsRating(String roomNo, int stars) {
        ScenarioWorld.ApiResponse rated = world.lastResponse();
        if (rated.status() != 200) {
            throw new AssertionError("評分預期 200，實際 status=" + rated.status() + "，body=" + rated.body());
        }
        JsonNode request = guestRequest(roomNo);
        check(roomNo + " 住客讀到的 rating", stars, request.path("rating").isInt() ? request.path("rating").asInt() : null);
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        Map<String, Object> task = dbProbe.taskRow(taskId).orElseThrow();
        check("tasks.rating", stars, task.get("rating") == null ? null : ((Number) task.get("rating")).intValue());
    }

    private JsonNode guestRequest(String roomNo) {
        String taskId = world.taskId(roomNo);
        ScenarioWorld.ApiResponse resp = apiClient.getUnversioned("/guest/requests", guestAuth(roomNo));
        if (resp.status() != 200 || resp.json() == null || !resp.json().isArray()) {
            throw new AssertionError("住客讀需求清單預期 200 與陣列，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        for (JsonNode request : resp.json()) {
            if (taskId.equalsIgnoreCase(request.path("id").asText(null))) {
                return request;
            }
        }
        throw new AssertionError(roomNo + " 的住客讀不到 id=" + taskId + " 的需求，body=" + resp.body());
    }

    private void requireTaskEnded(String roomNo, boolean ended) {
        Map<String, Object> task = dbProbe.taskRow(UUID.fromString(world.taskId(roomNo))).orElseThrow();
        if ((task.get("ended_at") != null) != ended) {
            throw new AssertionError(roomNo + " tasks.ended_at 預期" + (ended ? "有值" : "為空") + "，實際 " + task.get("ended_at"));
        }
    }

    private BddApiClient.Auth guestAuth(String roomNo) {
        return BddApiClient.Auth.guest(deskApi.guestSessionToken(roomNo));
    }

    private List<String> verifyQueueTicketNos(String managerName) {
        ScenarioWorld.ApiResponse resp = apiClient.get("/me/manager-queues", BddApiClient.Auth.staff(world.jwt(managerName)));
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError("getManagerQueues 預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        List<String> ticketNos = new ArrayList<>();
        for (JsonNode row : resp.json().path("verify")) {
            ticketNos.add(row.path("ticketNo").asText(null));
        }
        return ticketNos;
    }

    private String deskReaderName() {
        return dbProbe
                .queryOne(
                        """
                        SELECT name FROM staff_accounts
                         WHERE tenant_id = ? AND active AND role IN ('frontdesk', 'manager', 'duty_manager')
                         ORDER BY name LIMIT 1
                        """,
                        tenantFixtures.testTenantId())
                .map(row -> String.valueOf(row.get("name")))
                .orElseThrow(() -> new IllegalStateException("本場景沒有櫃檯、主管或值班主管，無法讀櫃檯詳情"));
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }
}
