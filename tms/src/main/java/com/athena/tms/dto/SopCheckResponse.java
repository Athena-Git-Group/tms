package com.athena.tms.dto;

import com.athena.tms.entity.TaskSopCheck;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SopCheckResponse(UUID taskId, String itemKey, String label, boolean checked, boolean skipped, UUID checkedBy, OffsetDateTime checkedAt) {
    public static SopCheckResponse from(TaskSopCheck check) {
        return new SopCheckResponse(check.getTaskId(), check.getItemKey(), check.getLabel(), check.isChecked(), check.isSkipped(), check.getCheckedBy(), check.getCheckedAt());
    }
}
