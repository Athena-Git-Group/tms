package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.dto.Actor;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.integration.stepdefs.utils.Tenants;
import io.cucumber.java.en.When;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.springframework.dao.DataAccessException;

public class InspectionQrWhenSteps {

    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;
    private final TaskFixtures fixtures;

    public InspectionQrWhenSteps(ScenarioContext ctx, ApiClient api, DbSupport db, TaskFixtures fixtures) {
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
        Tenants.runAsTenant(1L, () -> {
            fixtures.engine().start(1L, ctx.taskId(), cleaner);
            fixtures.engine().complete(1L, ctx.taskId(), cleaner);
        });
        ScenarioContext.HttpResult reinspected = inspect(ctx.taskId(), judgeFailing());
        Assertions.assertEquals(200, reinspected.status(), "重查該成功: " + reinspected.body());
        ctx.lastResponse(reinspected);
    }

    @When("^王主管列印房 (\\d+) 的 QR$")
    public void managerPrintsRoomQr(String roomNo) {
        ScenarioContext.HttpResult print = api.get("/admin/rooms/" + roomNo + "/qr", managerToken());
        ctx.put("printBody", print.body());
        ctx.lastResponse(print);
    }

    @When("^直接為房 502 插入第二枚 active token$")
    public void insertSecondActiveTokenForRoom502() {
        try {
            db.admin().update(
                    "INSERT INTO room_qr_tokens (id, tenant_id, room_id, token, version, status, created_at) "
                            + "VALUES (?, 1, ?, 'DupActiveProbe0000000AA', 99, 'active', now())",
                    UUID.randomUUID(), ctx.entityId("r-502"));
            Assertions.fail("同房第二枚 active 該被 partial unique 擋下");
        } catch (DataAccessException expected) {
            Throwable cause = expected.getMostSpecificCause();
            if (cause instanceof SQLException sqlException) {
                ctx.put("sqlState", sqlException.getSQLState());
            }
            Assertions.fail("該是 SQLException 根因: " + cause);
        }
    }

    @When("^王主管對房 (\\d+) 執行「重產此房 QR」$")
    public void managerRotatesRoomQr(String roomNo) {
        Map<String, Object> active = db.admin().queryForMap(
                "SELECT token, version FROM room_qr_tokens q JOIN rooms r ON r.id = q.room_id "
                        + "WHERE r.tenant_id = 1 AND r.room_no = ? AND q.status = 'active'",
                roomNo);
        ctx.put("oldQrToken", active.get("token"));
        ctx.put("oldQrVersion", ((Number) active.get("version")).intValue());
        ctx.lastResponse(api.postJson("/admin/rooms/" + roomNo + "/qr/rotate", managerToken(), Map.of(), null));
    }

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

    private ScenarioContext.HttpResult inspect(UUID taskId, Map<String, Object> body) {
        ctx.put("preInspectionState", db.taskRow(taskId).get("state"));
        return api.postJson("/admin/inspections/" + taskId, managerToken(), body, null);
    }

    private String managerToken() {
        return api.staffToken(1L, ctx.staffLogin("王主管"));
    }
}
