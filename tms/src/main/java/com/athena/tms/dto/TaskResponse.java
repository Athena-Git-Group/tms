package com.athena.tms.dto;

import com.athena.tms.entity.Task;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskResponse(UUID id, String type, String source, UUID roomId, UUID reservationId, String dept, String state, UUID assigneeId, String title, String note, Integer estMin, OffsetDateTime startedAt, OffsetDateTime doneAt, int redoCount, Integer rating, UUID planId, LocalDate workDate, int eventSeq, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getType(), task.getSource(), task.getRoomId(), task.getReservationId(), task.getDept(), task.getState(), task.getAssigneeId(), task.getTitle(), task.getNote(), task.getEstMin(), task.getStartedAt(), task.getDoneAt(), task.getRedoCount(), task.getRating(), task.getPlanId(), task.getWorkDate(), task.getEventSeq(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
