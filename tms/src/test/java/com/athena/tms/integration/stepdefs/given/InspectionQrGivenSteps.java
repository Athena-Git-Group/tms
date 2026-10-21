package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.java.en.Given;

import java.time.LocalTime;

/** 04-inspection／06-qr-lifecycle feature 的前置步驟（表格類資料由 EntityTableGivenSteps 處理）。 */
public class InspectionQrGivenSteps {

    private final ScenarioContext ctx;
    private final DbSupport db;

    public InspectionQrGivenSteps(ScenarioContext ctx, DbSupport db) {
        this.ctx = ctx;
        this.db = db;
    }

    @Given("^租戶 1 的班次設定涵蓋現在，清房者視為在班$")
    public void shiftTimesCoverNow() {
        // 全日班（start==end 視為 24h）：任何時刻跑測試，清房者都「在班」
        setShiftTimes("00:00", "00:00");
    }

    @Given("^照租戶班次設定「小美」已下班$")
    public void shiftTimesExcludeNow() {
        // 時間窗設成「兩小時前就收班」：任何時刻跑測試都「已下班」
        LocalTime now = LocalTime.now();
        setShiftTimes(hhmm(now.minusHours(3)), hhmm(now.minusHours(2)));
    }

    @Given("^房 301 尚無任何 QR token$")
    public void room301HasNoQrToken() {
        db.admin().update("DELETE FROM room_qr_tokens WHERE room_id = ?", ctx.entityId("r-301"));
    }

    private void setShiftTimes(String start, String end) {
        db.admin().update("UPDATE tenant_config SET value = ?::jsonb"
                        + " WHERE tenant_id = 1 AND key = 'shift_times'",
                "{\"morning\": {\"start\": \"" + start + "\", \"end\": \"" + end + "\"}}");
    }

    private static String hhmm(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}
