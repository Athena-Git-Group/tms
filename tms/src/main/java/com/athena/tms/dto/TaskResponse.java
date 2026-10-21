package com.athena.tms.dto;

import com.athena.tms.entity.Task;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 任務回應（staff 與 desk 共用同一形狀）：逐欄映照 tasks 表——tasks 是從
 * task_events 事件紀錄整理出來、方便查詢的目前狀態副本，欄名 camelCase
 * 與需求文件用詞一致、不自創縮寫。實際分鐘是 derived（doneAt−startedAt），
 * 刻意不加欄位——加了就是第二真相，遲早跟兩個時間戳漂移。
 *
 * <p>desk 套件直接 import 本類而不是複製一份：兩邊看的是同一張 tasks 查詢用
 * 副本、同一組欄位，複製出第二個 record 才是漂移的開始（先例：idempotency
 * 套件共用 outbox 的 TenantScopedExecutor）。
 */
public record TaskResponse(
        UUID id,
        String type,
        String source,
        UUID roomId,
        UUID reservationId,
        String dept,
        String state,
        UUID assigneeId,
        String title,
        String note,
        Integer estMin,
        OffsetDateTime startedAt,
        OffsetDateTime doneAt,
        int redoCount,
        Integer rating,
        UUID planId,
        LocalDate workDate,
        int eventSeq,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getType(),
                task.getSource(),
                task.getRoomId(),
                task.getReservationId(),
                task.getDept(),
                task.getState(),
                task.getAssigneeId(),
                task.getTitle(),
                task.getNote(),
                task.getEstMin(),
                task.getStartedAt(),
                task.getDoneAt(),
                task.getRedoCount(),
                task.getRating(),
                task.getPlanId(),
                task.getWorkDate(),
                task.getEventSeq(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
