package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.java.en.Given;

public class InspectionQrGivenSteps {

    private final ScenarioContext ctx;
    private final DbSupport db;

    public InspectionQrGivenSteps(ScenarioContext ctx, DbSupport db) {
        this.ctx = ctx;
        this.db = db;
    }

    @Given("^租戶 1 的班次設定涵蓋現在，清房者視為在班$")
    public void shiftTimesCoverNow() {
        setShiftTimes("00:00", "00:00");
    }

    @Given("^照租戶班次設定「小美」已下班$")
    public void shiftTimesExcludeNow() {
        setShiftTimes("08:00", "10:00");
    }

    @Given("^租戶 1 (允許|不允許)主管查自己清的房$")
    public void selfInspectionAllowed(String allowOrNot) {
        db.admin().update(
                """
                INSERT INTO tenant_config (tenant_id, key, value, updated_at)
                VALUES (1, 'self_inspection_allowed', ?::jsonb, now())
                ON CONFLICT (tenant_id, key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()
                """, "允許".equals(allowOrNot) ? "true" : "false");
    }

    @Given("^房 301 尚無任何 QR token$")
    public void room301HasNoQrToken() {
        db.admin().update("DELETE FROM room_qr_tokens WHERE room_id = ?", ctx.entityId("r-301"));
    }

    private void setShiftTimes(String start, String end) {
        db.admin().update(
                "UPDATE tenant_config SET value = ?::jsonb WHERE tenant_id = 1 AND key = 'shift_times'",
                "{\"morning\": {\"start\": \"" + start + "\", \"end\": \"" + end + "\"}}");
    }
}
