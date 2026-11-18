package com.athena.tms.dto;

import com.athena.tms.entity.Task;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 住客進度頁的一則需求；{@code answer} 是櫃檯當場回答的回覆，其他需求為 null。 */
public record GuestTaskView(UUID id, String type, String state, String dept, String title, String answer, Integer rating, OffsetDateTime createdAt) {
    public static GuestTaskView from(Task task) {
        return new GuestTaskView(task.getId(), task.getType(), task.getState(), task.getDept(), task.getTitle(), task.getAnswer(), task.getRating(), task.getCreatedAt());
    }
}
