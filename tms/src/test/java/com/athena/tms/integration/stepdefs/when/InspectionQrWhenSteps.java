package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.Tenants;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.dto.Actor;
import io.cucumber.java.en.When;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/** 04-inspection／06-qr-lifecycle feature 的動作步驟。 */
public class InspectionQrWhenSteps {

    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;
    private final TaskFixtures fixtures;

    public InspectionQrWhenSteps(ScenarioContext ctx, ApiClient api, DbSupport db,
                                 TaskFixtures fixtures) {
        this.ctx = ctx;
        this.api = api;
        this.db = db;
        this.fixtures = fixtures;
    }

    @When("^王主管對任務 \"([^\"]+)\" 送出全過的查房$")
    public void managerSubmitsAllPassInspection(String taskSymbol) {
        ctx.taskId(ctx.entityId(taskSymbol));
        ctx.lastResponse(inspect(ctx.taskId(), judgeFailing()));
    }

    @When("^王主管對任務 \"([^\"]+)\" 只送出 ([a-z_]+)、([a-z_]+) 兩項判定$")
    public void managerSubmitsSubset(String taskSymbol, String first, String second) {
        ctx.taskId(ctx.entityId(taskSymbol));
        Map<String, Boolean> judgements = new LinkedHashMap<>();
        judgements.put(first, true);
        judgements.put(second, true);
        ctx.lastResponse(inspect(ctx.taskId(), judgement(judgements)));
    }

    @When("^王主管對任務 \"([^\"]+)\" 送出全過的查房，但夾帶清單外的項目 \"([a-z_]+)\"$")
    public void managerSubmitsUnknownKey(String taskSymbol, String unknownKey) {
        ctx.taskId(ctx.entityId(taskSymbol));
        Map<String, Boolean> judgements = new LinkedHashMap<>();
        inspectionKeys().forEach(key -> judgements.put(key, true));
        judgements.put(unknownKey, true);
        ctx.lastResponse(inspect(ctx.taskId(), judgement(judgements)));
    }

    @When("^王主管判定 ([a-z_]+) 不過、其餘全過$")
    public void managerFailsOneItem(String failKey) {
        ctx.lastResponse(inspect(ctx.taskId(), judgeFailing(failKey)));
    }

    @When("^原清房者重掃重交、王主管再送出全過的查房$")
    public void cleanerRedoesThenReinspectionPasses() {
        Actor cleaner = ctx.get("cleanerActor");
        Tenants.runAsTenant(TENANT_1, () -> {
            fixtures.engine().start(TENANT_1, ctx.taskId(), cleaner);
            fixtures.engine().complete(TENANT_1, ctx.taskId(), cleaner);
        });
        HttpResult reinspected = inspect(ctx.taskId(), judgeFailing());
        assertEquals(200, reinspected.status(), "重查該成功: " + reinspected.body());
        ctx.lastResponse(reinspected);
    }

    @When("^王主管列印房 (\\d+) 的 QR$")
    public void managerPrintsRoomQr(String roomNo) {
        HttpResult print = api.get("/admin/rooms/" + roomNo + "/qr", managerToken());
        ctx.put("printBody", print.body());
        ctx.lastResponse(print);
    }

    @When("^直接為房 502 插入第二枚 active token$")
    public void insertSecondActiveTokenForRoom502() {
        try {
            db.admin().update("INSERT INTO room_qr_tokens (id, tenant_id, room_id, token,"
                            + " version, status, created_at) VALUES (?, 1, ?,"
                            + " 'DupActiveProbe0000000AA', 99, 'active', now())",
                    UUID.randomUUID(), ctx.entityId("r-502"));
            fail("同房第二枚 active 該被 partial unique 擋下");
        } catch (DataAccessException expected) {
            Throwable cause = expected.getMostSpecificCause();
            if (cause instanceof SQLException sqlException) {
                ctx.put("sqlState", sqlException.getSQLState());
            } else {
                fail("該是 SQLException 根因: " + cause);
            }
        }
    }

    @When("^王主管對房 (\\d+) 執行「重產此房 QR」$")
    public void managerRotatesRoomQr(String roomNo) {
        // 輪替斷言要對照「輪替前」的現行 token 與版本，先從資料庫取下來
        Map<String, Object> active = db.admin().queryForMap(
                "SELECT token, version FROM room_qr_tokens q JOIN rooms r ON r.id = q.room_id"
                        + " WHERE r.tenant_id = 1 AND r.room_no = ? AND q.status = 'active'",
                roomNo);
        ctx.put("oldQrToken", active.get("token"));
        ctx.put("oldQrVersion", ((Number) active.get("version")).intValue());
        ctx.lastResponse(api.postJson("/admin/rooms/" + roomNo + "/qr/rotate",
                managerToken(), Map.of(), null));
    }

    /** 全集判定、指定鍵不過（不給鍵＝全過）。 */
    private Map<String, Object> judgeFailing(String... failKeys) {
        Map<String, Boolean> judgements = new LinkedHashMap<>();
        inspectionKeys().forEach(key -> judgements.put(key, true));
        for (String failKey : failKeys) {
            judgements.put(failKey, false);
        }
        return judgement(judgements);
    }

    private Map<String, Object> judgement(Map<String, Boolean> judgements) {
        List<Map<String, Object>> items = new ArrayList<>();
        judgements.forEach((key, pass) -> items.add(Map.of("key", key, "pass", pass)));
        return Map.of("items", items);
    }

    private List<String> inspectionKeys() {
        List<String> keys = ctx.get("inspectionKeys");
        if (keys == null) {
            throw new IllegalStateException("查房項目全集尚未就緒——背景的 SOP項目 表格漏了");
        }
        return keys;
    }

    private HttpResult inspect(UUID taskId, Map<String, Object> body) {
        // 被擋的查房「任務狀態不變」要對照送出前的狀態
        ctx.put("preInspectionState", db.taskRow(taskId).get("state"));
        return api.postJson("/admin/inspections/" + taskId, managerToken(), body, null);
    }

    private String managerToken() {
        return api.staffToken(TENANT_1, ctx.staffLogin("王主管"));
    }
}
