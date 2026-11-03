/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.cucumber.java.en.Given
 */
package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.java.en.Given;
import java.time.LocalTime;

public class InspectionQrGivenSteps {
    private final ScenarioContext ctx;
    private final DbSupport db;

    public InspectionQrGivenSteps(ScenarioContext ctx, DbSupport db) {
        this.ctx = ctx;
        this.db = db;
    }

    @Given(value="^\u79df\u6236 1 \u7684\u73ed\u6b21\u8a2d\u5b9a\u6db5\u84cb\u73fe\u5728\uff0c\u6e05\u623f\u8005\u8996\u70ba\u5728\u73ed$")
    public void shiftTimesCoverNow() {
        this.setShiftTimes("00:00", "00:00");
    }

    @Given(value="^\u7167\u79df\u6236\u73ed\u6b21\u8a2d\u5b9a\u300c\u5c0f\u7f8e\u300d\u5df2\u4e0b\u73ed$")
    public void shiftTimesExcludeNow() {
        LocalTime now = LocalTime.now();
        this.setShiftTimes(InspectionQrGivenSteps.hhmm(now.minusHours(3L)), InspectionQrGivenSteps.hhmm(now.minusHours(2L)));
    }

    @Given(value="^\u623f 301 \u5c1a\u7121\u4efb\u4f55 QR token$")
    public void room301HasNoQrToken() {
        this.db.admin().update("DELETE FROM room_qr_tokens WHERE room_id = ?", new Object[]{this.ctx.entityId("r-301")});
    }

    private void setShiftTimes(String start, String end) {
        this.db.admin().update("UPDATE tenant_config SET value = ?::jsonb WHERE tenant_id = 1 AND key = 'shift_times'", new Object[]{"{\"morning\": {\"start\": \"" + start + "\", \"end\": \"" + end + "\"}}"});
    }

    private static String hhmm(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}
