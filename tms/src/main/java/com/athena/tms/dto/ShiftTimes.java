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
