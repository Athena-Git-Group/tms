package com.athena.tms.dto.desk;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeskTask(
        UUID id,
        int seq,
        String displayNo,
        String state,
        boolean answered,
        String dept,
        String type,
        String source,
        String roomNo,
        int floor,
        boolean vip,
        String guestName,
        String title,
        boolean guestTyped,
        DeskGuestText guestText,
        String menuKey,
        UUID assigneeId,
        String assigneeName,
        OffsetDateTime createdAt,
        OffsetDateTime waitingSince,
        OffsetDateTime acceptedAt,
        String acceptedVia,
        OffsetDateTime startedAt,
        OffsetDateTime doneAt,
        OffsetDateTime interruptedAt,
        OffsetDateTime endedAt,
        OffsetDateTime updatedAt,
        String inspection,
        Sop sop,
        Interruption interruption,
        Deferral deferral) {

    public record Sop(int done, int total) {}

    public record Interruption(boolean bySelf, String reason, String handoverNote) {}

    public record Deferral(boolean bySelf, String byName, String reason) {}
}
