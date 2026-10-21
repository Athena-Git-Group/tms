package com.athena.tms.dto;

import tools.jackson.databind.JsonNode;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * tenant_config `shift_times` 的判讀（退回連動的「已下班」判定依據）。
 * 形狀：{"morning": {"start": "08:00", "end": "16:00"}, ...}——班次名稱是
 * 租戶自己取的，這裡只看每個班次的時間窗，不認名字。
 *
 * <p>判定哲學是「寧可少做不可亂做」：連動退池只是便民加速（省主管一次手動
 * requeue），設定缺漏或格式壞掉時自動退池反而可能把人還在做的單搶走——
 * 所以任何解讀不了的情況一律當「在班」，讓流程退回預設的人工路徑。
 */
public final class ShiftTimes {

    private ShiftTimes() {
    }

    /**
     * @return at 時刻是否落在任一班次時間窗內；設定缺漏／無法解析一律 true（視為在班）
     */
    public static boolean coversTime(JsonNode shiftTimes, LocalTime at) {
        if (shiftTimes == null || !shiftTimes.isObject()) {
            return true;
        }
        boolean anyParsableWindow = false;
        for (Map.Entry<String, JsonNode> shift : shiftTimes.properties()) {
            JsonNode window = shift.getValue();
            if (window == null || !window.isObject()) {
                continue;
            }
            LocalTime start = parseTime(window.get("start"));
            LocalTime end = parseTime(window.get("end"));
            if (start == null || end == null) {
                continue;
            }
            anyParsableWindow = true;
            if (windowCovers(start, end, at)) {
                return true;
            }
        }
        return !anyParsableWindow;
    }

    /**
     * end <= start 視為跨午夜班（夜班 23:00–07:00 是旅宿常態，不支援它
     * 等於夜班員工天天被誤判成已下班）；start == end 視為全日班。
     */
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
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
