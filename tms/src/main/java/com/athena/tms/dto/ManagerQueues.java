package com.athena.tms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ManagerQueues(
        Counts counts,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<InspectionQueueItem> inspection,
        List<VerifyQueueItem> verify) {

    public static ManagerQueues of(List<InspectionQueueItem> inspection, List<VerifyQueueItem> verify) {
        return new ManagerQueues(
                new Counts(inspection == null ? null : inspection.size(), verify.size()),
                inspection,
                verify);
    }

    public record Counts(@JsonInclude(JsonInclude.Include.NON_NULL) Integer inspection, int verify) {}

    public record InspectionQueueItem(
            UUID taskId,
            String roomNo,
            boolean isPublicArea,
            String title,
            OffsetDateTime waitingSince,
            boolean waitingSinceIsToday,
            String cleanerName,
            int redoCount) {}

    public record VerifyQueueItem(
            UUID taskId,
            String roomNo,
            boolean isPublicArea,
            String title,
            OffsetDateTime waitingSince,
            boolean waitingSinceIsToday,
            String ticketNo,
            String category,
            String description,
            String reporterName,
            String assigneeName,
            String origin,
            String resolutionType,
            boolean wasVerificationFailed) {}
}
