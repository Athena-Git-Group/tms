package com.athena.tms.dto;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class ShiftTimes {
    private ShiftTimes() {
    }

    public static boolean coversTime(JsonNode shiftTimes, LocalTime at) {
        if (shiftTimes == null || !shiftTimes.isObject()) {
            return true;
        }
        boolean anyParsableWindow = false;
        for (Map.Entry shift : shiftTimes.properties()) {
            JsonNode window = (JsonNode)shift.getValue();
            if (window == null || !window.isObject()) continue;
            LocalTime start = ShiftTimes.parseTime(window.get("start"));
            LocalTime end = ShiftTimes.parseTime(window.get("end"));
            if (start == null || end == null) continue;
            anyParsableWindow = true;
            if (!ShiftTimes.windowCovers(start, end, at)) continue;
            return true;
        }
        return !anyParsableWindow;
    }

    /**
     * 排班表上的班次欄位換成下班時間。班次欄位可能直接存 {@code HH:mm}，也可能是飯店班別設定
     * （tenant_config 的 shift_times）裡的班別名稱，此時取該班別的 end。兩者都解析不出來時回 null，
     * 呼叫端應視為「不知道幾點下班」而不是「已經下班」。
     */
    public static LocalTime endOf(String shift, JsonNode shiftTimes) {
        if (shift == null) {
            return null;
        }
        try {
            return LocalTime.parse(shift);
        }
        catch (DateTimeParseException notHHmm) {
            if (shiftTimes == null || !shiftTimes.isObject()) {
                return null;
            }
            JsonNode window = shiftTimes.get(shift);
            if (window == null || !window.isObject()) {
                return null;
            }
            return ShiftTimes.parseTime(window.get("end"));
        }
    }

    private static boolean windowCovers(LocalTime start, LocalTime end, LocalTime at) {
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !at.isBefore(start) && at.isBefore(end);
        }
        return !at.isBefore(start) || at.isBefore(end);
    }

    private static LocalTime parseTime(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            return LocalTime.parse(node.asText());
        }
        catch (DateTimeParseException e) {
            return null;
        }
    }
}
