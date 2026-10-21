package com.athena.tms.dto;

import com.athena.tms.entity.TaskSopCheck;

import java.time.OffsetDateTime;
import java.util.UUID;

/** SOP 勾記回應：這一項此刻的勾記狀態（label 是快照，項目日後改名不影響歷史單）。 */
public record SopCheckResponse(
        UUID taskId,
        String itemKey,
        String label,
        boolean checked,
        boolean skipped,
        UUID checkedBy,
        OffsetDateTime checkedAt) {

    public static SopCheckResponse from(TaskSopCheck check) {
        return new SopCheckResponse(
                check.getTaskId(),
                check.getItemKey(),
                check.getLabel(),
                check.isChecked(),
                check.isSkipped(),
                check.getCheckedBy(),
                check.getCheckedAt());
    }
}
