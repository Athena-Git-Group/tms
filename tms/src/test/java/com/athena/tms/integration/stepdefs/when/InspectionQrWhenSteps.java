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

    @When(value="^王主管對任務 \"([^\"]+)\" 送出全過的查房$")
    public void managerSubmitsAllPassInspection(String taskSymbol) {
        this.ctx.taskId(this.ctx.entityId(taskSymbol));
        this.ctx.lastResponse(this.inspect(this.ctx.taskId(), this.judgeFailing(new String[0])));
    }

    @When(value="^王主管對任務 \"([^\"]+)\" 只送出 ([a-z_]+)、([a-z_]+) 兩項判定$")
    public void managerSubmitsSubset(String taskSymbol, String first, String second) {
        this.ctx.taskId(this.ctx.entityId(taskSymbol));
        LinkedHashMap<String, Boolean> judgements = new LinkedHashMap<String, Boolean>();
        judgements.put(first, true);
        judgements.put(second, true);
        this.ctx.lastResponse(this.inspect(this.ctx.taskId(), this.judgement(judgements)));
    }

    @When(value="^王主管對任務 \"([^\"]+)\" 送出全過的查房，但夾帶清單外的項目 \"([a-z_]+)\"$")
    public void managerSubmitsUnknownKey(String taskSymbol, String unknownKey) {
        this.ctx.taskId(this.ctx.entityId(taskSymbol));
        LinkedHashMap<String, Boolean> judgements = new LinkedHashMap<String, Boolean>();
        this.inspectionKeys().forEach(key -> judgements.put((String)key, true));
        judgements.put(unknownKey, true);
        this.ctx.lastResponse(this.inspect(this.ctx.taskId(), this.judgement(judgements)));
    }

    @When(value="^王主管判定 ([a-z_]+) 不過、其餘全過$")
    public void managerFailsOneItem(String failKey) {
        this.ctx.lastResponse(this.inspect(this.ctx.taskId(), this.judgeFailing(failKey)));
    }

    @When(value="^原清房者重掃重交、王主管再送出全過的查房$")
    public void cleanerRedoesThenReinspectionPasses() {
        Actor cleaner = (Actor)this.ctx.get("cleanerActor");
        Tenants.runAsTenant(1L, () -> {
            this.fixtures.engine().start(1L, this.ctx.taskId(), cleaner);
            this.fixtures.engine().complete(1L, this.ctx.taskId(), cleaner);
        });
        ScenarioContext.HttpResult reinspected = this.inspect(this.ctx.taskId(), this.judgeFailing(new String[0]));
        Assertions.assertEquals((int)200, (int)reinspected.status(), (String)("重查該成功: " + reinspected.body()));
        this.ctx.lastResponse(reinspected);
    }

    @When(value="^王主管列印房 (\\d+) 的 QR$")
    public void managerPrintsRoomQr(String roomNo) {
        ScenarioContext.HttpResult print = this.api.get("/admin/rooms/" + roomNo + "/qr", this.managerToken());
        this.ctx.put("printBody", print.body());
        this.ctx.lastResponse(print);
    }

    @When(value="^直接為房 502 插入第二枚 active token$")
    public void insertSecondActiveTokenForRoom502() {
        try {
            this.db.admin().update("INSERT INTO room_qr_tokens (id, tenant_id, room_id, token, version, status, created_at) VALUES (?, 1, ?, 'DupActiveProbe0000000AA', 99, 'active', now())", new Object[]{UUID.randomUUID(), this.ctx.entityId("r-502")});
            Assertions.fail((String)"同房第二枚 active 該被 partial unique 擋下");
        }
        catch (DataAccessException expected) {
            Throwable cause = expected.getMostSpecificCause();
            if (cause instanceof SQLException) {
                SQLException sqlException = (SQLException)cause;
                this.ctx.put("sqlState", sqlException.getSQLState());
            }
            Assertions.fail((String)("該是 SQLException 根因: " + String.valueOf(cause)));
        }
    }

    @When(value="^王主管對房 (\\d+) 執行「重產此房 QR」$")
    public void managerRotatesRoomQr(String roomNo) {
        Map active = this.db.admin().queryForMap("SELECT token, version FROM room_qr_tokens q JOIN rooms r ON r.id = q.room_id WHERE r.tenant_id = 1 AND r.room_no = ? AND q.status = 'active'", new Object[]{roomNo});
        this.ctx.put("oldQrToken", active.get("token"));
        this.ctx.put("oldQrVersion", ((Number)active.get("version")).intValue());
        this.ctx.lastResponse(this.api.postJson("/admin/rooms/" + roomNo + "/qr/rotate", this.managerToken(), Map.of(), null));
    }

    private Map<String, Object> judgeFailing(String ... failKeys) {
        LinkedHashMap<String, Boolean> judgements = new LinkedHashMap<String, Boolean>();
        this.inspectionKeys().forEach(key -> judgements.put((String)key, true));
        for (String failKey : failKeys) {
            judgements.put(failKey, false);
        }
        return this.judgement(judgements);
    }

    private Map<String, Object> judgement(Map<String, Boolean> judgements) {
        ArrayList items = new ArrayList();
        judgements.forEach((key, pass) -> items.add(Map.of("key", key, "pass", pass)));
        return Map.of("items", items);
    }

    private List<String> inspectionKeys() {
        List keys = (List)this.ctx.get("inspectionKeys");
        if (keys == null) {
            throw new IllegalStateException("查房項目全集尚未就緒——背景的 SOP項目 表格漏了");
        }
        return keys;
    }

    private ScenarioContext.HttpResult inspect(UUID taskId, Map<String, Object> body) {
        this.ctx.put("preInspectionState", this.db.taskRow(taskId).get("state"));
        return this.api.postJson("/admin/inspections/" + String.valueOf(taskId), this.managerToken(), body, null);
    }

    private String managerToken() {
        return this.api.staffToken(1L, this.ctx.staffLogin("王主管"));
    }
}
