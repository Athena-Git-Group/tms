/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.dto;

import com.athena.tms.entity.Task;
import java.time.OffsetDateTime;
import java.util.UUID;

public record GuestTaskView(UUID id, String type, String state, String dept, String title, Integer rating, OffsetDateTime createdAt) {
    public static GuestTaskView from(Task task) {
        return new GuestTaskView(task.getId(), task.getType(), task.getState(), task.getDept(), task.getTitle(), task.getRating(), task.getCreatedAt());
    }
}
