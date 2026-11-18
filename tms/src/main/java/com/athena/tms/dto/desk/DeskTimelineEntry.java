package com.athena.tms.dto.desk;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

public record DeskTimelineEntry(
        int seq,
        OffsetDateTime at,
        String type,
        Actor actor,
        @JsonInclude(JsonInclude.Include.NON_NULL) Detail detail) {

    public record Actor(String kind, String role, String name) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            String dept,
            String initialState,
            Boolean guestTyped,
            String ruleLabel,
            String via,
            String answer,
            String assigneeName,
            String previousAssigneeName,
            String reason,
            Boolean bySelf,
            String note,
            String cancelReason,
            String resolutionType,
            Integer rating) {}
}
