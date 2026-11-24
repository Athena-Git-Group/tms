package com.athena.tms.repair;

import java.util.UUID;

public record RepairTicketDraft(
        String category,
        String description,
        UUID reporterId,
        UUID sourceTaskId,
        UUID originTicketTaskId) {
}
