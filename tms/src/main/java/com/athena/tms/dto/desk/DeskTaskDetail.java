package com.athena.tms.dto.desk;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record DeskTaskDetail(
        @JsonUnwrapped DeskTask task,
        OffsetDateTime serverTime,
        String answer,
        String answeredByName,
        Guest guest,
        String createdByName,
        Accepted accepted,
        Cancellation cancellation,
        Repair repair,
        List<DeskTimelineEntry> timeline) {

    public record Guest(String name, LocalDate checkinDate, LocalDate checkoutDate) {}

    public record Accepted(String via, OffsetDateTime at, String byName, String previousAssigneeName) {}

    public record Cancellation(String reason, String byName) {}

    /** photos、completionPhotos 與工務單詳情同一份清單（同排序、完修照片只有這一輪）。 */
    public record Repair(
            String resolutionType,
            String verifiedByName,
            List<Map<String, Object>> photos,
            List<Map<String, Object>> completionPhotos) {}
}
