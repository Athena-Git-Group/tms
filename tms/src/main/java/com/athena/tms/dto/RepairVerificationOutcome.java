package com.athena.tms.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RepairVerificationOutcome(
        UUID taskId,
        String ticketNo,
        String roomNo,
        boolean isPublicArea,
        String result,
        String destination,
        String assigneeName,
        String reason,
        String decidedBy,
        OffsetDateTime decidedAt) {
}
