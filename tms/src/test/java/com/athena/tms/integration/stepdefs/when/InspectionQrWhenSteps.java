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

    @When(value="^\u738b\u4e3b\u7ba1\u5c0d\u4efb\u52d9 \"([^\"]+)\" \u9001\u51fa\u5168\u904e\u7684\u67e5\u623f$")
    public void managerSubmitsAllPassInspection(String taskSymbol) {
        this.ctx.taskId(this.ctx.entityId(taskSymbol));
        this.ctx.lastResponse(this.inspect(this.ctx.taskId(), this.judgeFailing(new String[0])));
    }

    @When(value="^\u738b\u4e3b\u7ba1\u5c0d\u4efb\u52d9 \"([^\"]+)\" \u53ea\u9001\u51fa ([a-z_]+)\u3001([a-z_]+) \u5169\u9805\u5224\u5b9a$")
    public void managerSubmitsSubset(String taskSymbol, String first, String second) {
        this.ctx.taskId(this.ctx.entityId(taskSymbol));
        LinkedHashMap<String, Boolean> judgements = new LinkedHashMap<String, Boolean>();
        judgements.put(first, true);
        judgements.put(second, true);
        this.ctx.lastResponse(this.inspect(this.ctx.taskId(), this.judgement(judgements)));
    }

    @When(value="^\u738b\u4e3b\u7ba1\u5c0d\u4efb\u52d9 \"([^\"]+)\" \u9001\u51fa\u5168\u904e\u7684\u67e5\u623f\uff0c\u4f46\u593e\u5e36\u6e05\u55ae\u5916\u7684\u9805\u76ee \"([a-z_]+)\"$")
    public void managerSubmitsUnknownKey(String taskSymbol, String unknownKey) {
        this.ctx.taskId(this.ctx.entityId(taskSymbol));
        LinkedHashMap<String, Boolean> judgements = new LinkedHashMap<String, Boolean>();
        this.inspectionKeys().forEach(key -> judgements.put((String)key, true));
        judgements.put(unknownKey, true);
        this.ctx.lastResponse(this.inspect(this.ctx.taskId(), this.judgement(judgements)));
    }

    @When(value="^\u738b\u4e3b\u7ba1\u5224\u5b9a ([a-z_]+) \u4e0d\u904e\u3001\u5176\u9918\u5168\u904e$")
    public void managerFailsOneItem(String failKey) {
        this.ctx.lastResponse(this.inspect(this.ctx.taskId(), this.judgeFailing(failKey)));
    }

    @When(value="^\u539f\u6e05\u623f\u8005\u91cd\u6383\u91cd\u4ea4\u3001\u738b\u4e3b\u7ba1\u518d\u9001\u51fa\u5168\u904e\u7684\u67e5\u623f$")
    public void cleanerRedoesThenReinspectionPasses() {
        Actor cleaner = (Actor)this.ctx.get("cleanerActor");
        Tenants.runAsTenant(1L, () -> {
            this.fixtures.engine().start(1L, this.ctx.taskId(), cleaner);
            this.fixtures.engine().complete(1L, this.ctx.taskId(), cleaner);
        });
        ScenarioContext.HttpResult reinspected = this.inspect(this.ctx.taskId(), this.judgeFailing(new String[0]));
        Assertions.assertEquals((int)200, (int)reinspected.status(), (String)("\u91cd\u67e5\u8a72\u6210\u529f: " + reinspected.body()));
        this.ctx.lastResponse(reinspected);
    }

    @When(value="^\u738b\u4e3b\u7ba1\u5217\u5370\u623f (\\d+) \u7684 QR$")
    public void managerPrintsRoomQr(String roomNo) {
        ScenarioContext.HttpResult print = this.api.get("/admin/rooms/" + roomNo + "/qr", this.managerToken());
        this.ctx.put("printBody", print.body());
        this.ctx.lastResponse(print);
    }

    @When(value="^\u76f4\u63a5\u70ba\u623f 502 \u63d2\u5165\u7b2c\u4e8c\u679a active token$")
    public void insertSecondActiveTokenForRoom502() {
        try {
            this.db.admin().update("INSERT INTO room_qr_tokens (id, tenant_id, room_id, token, version, status, created_at) VALUES (?, 1, ?, 'DupActiveProbe0000000AA', 99, 'active', now())", new Object[]{UUID.randomUUID(), this.ctx.entityId("r-502")});
            Assertions.fail((String)"\u540c\u623f\u7b2c\u4e8c\u679a active \u8a72\u88ab partial unique \u64cb\u4e0b");
        }
        catch (DataAccessException expected) {
            Throwable cause = expected.getMostSpecificCause();
            if (cause instanceof SQLException) {
                SQLException sqlException = (SQLException)cause;
                this.ctx.put("sqlState", sqlException.getSQLState());
            }
            Assertions.fail((String)("\u8a72\u662f SQLException \u6839\u56e0: " + String.valueOf(cause)));
        }
    }

    @When(value="^\u738b\u4e3b\u7ba1\u5c0d\u623f (\\d+) \u57f7\u884c\u300c\u91cd\u7522\u6b64\u623f QR\u300d$")
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
            throw new IllegalStateException("\u67e5\u623f\u9805\u76ee\u5168\u96c6\u5c1a\u672a\u5c31\u7dd2\u2014\u2014\u80cc\u666f\u7684 SOP\u9805\u76ee \u8868\u683c\u6f0f\u4e86");
        }
        return keys;
    }

    private ScenarioContext.HttpResult inspect(UUID taskId, Map<String, Object> body) {
        this.ctx.put("preInspectionState", this.db.taskRow(taskId).get("state"));
        return this.api.postJson("/admin/inspections/" + String.valueOf(taskId), this.managerToken(), body, null);
    }

    private String managerToken() {
        return this.api.staffToken(1L, this.ctx.staffLogin("\u738b\u4e3b\u7ba1"));
    }
}
