package com.athena.tms.dto;

import com.athena.tms.dto.Actor;
import java.time.LocalDate;
import java.util.UUID;

public record TaskCreateCommand(long tenantId, String type, String source, UUID roomId, UUID reservationId, String dept, String initialState, UUID assigneeId, String title, String note, Integer estMin, UUID planId, LocalDate workDate, Actor actor) {
}
