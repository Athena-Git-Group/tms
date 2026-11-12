package com.athena.tms.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InspectionOutcome(
        UUID inspectionId,
        UUID taskId,
        String roomNo,
        String result,
        String destination,
        String cleanerName,
        List<FailedItem> failedItems,
        OffsetDateTime createdAt) {

    public record FailedItem(String key, String label) {}
}
