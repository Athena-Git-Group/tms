package com.athena.tms.dto;

import com.athena.tms.entity.Task;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 任務的住客可見面。刻意不含 assignee／note／est_min：住客只需要「進行到哪」
 * （state）與自己提了什麼；員工身分與內部備註屬員工端視圖，從住客端 API
 * 洩出去就是 PII 外流（員工姓名對住客是別人的個資）。
 */
public record GuestTaskView(
        UUID id,
        String type,
        String state,
        String dept,
        String title,
        Integer rating,
        OffsetDateTime createdAt) {

    public static GuestTaskView from(Task task) {
        return new GuestTaskView(task.getId(), task.getType(), task.getState(), task.getDept(),
                task.getTitle(), task.getRating(), task.getCreatedAt());
    }
}
