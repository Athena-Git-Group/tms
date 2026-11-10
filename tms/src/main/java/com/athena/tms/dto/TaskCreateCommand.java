package com.athena.tms.dto;

import com.athena.tms.dto.Actor;
import java.time.LocalDate;
import java.util.UUID;

public record TaskCreateCommand(long tenantId, String type, String source, UUID roomId, UUID reservationId, String dept, String initialState, UUID assigneeId, String title, String note, Integer estMin, UUID planId, LocalDate workDate, Actor actor, boolean urgent) {
    public TaskCreateCommand(long tenantId, String type, String source, UUID roomId, UUID reservationId, String dept, String initialState, UUID assigneeId, String title, String note, Integer estMin, UUID planId, LocalDate workDate, Actor actor) {
        this(tenantId, type, source, roomId, reservationId, dept, initialState, assigneeId, title, note, estMin, planId, workDate, actor, false);
    }
}
