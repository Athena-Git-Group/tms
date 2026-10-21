package com.athena.tms.dto;

import java.util.UUID;

/**
 * 一筆要分發的任務變化：envelope 自述欄位（task_id/seq/type/occurred_at）
 * 加上路由所需的任務歸屬（dept/assignee/reservation——查 tasks 當下值，
 * 不是事件當下值）。路由用「當下值」是刻意的：SSE 只是變更鈴鐺，客戶端
 * 收到後重拉快照，真相在資料庫；用事件當下值反而要重建歷史。
 */
public record TaskChange(
        long tenantId,
        UUID taskId,
        int seq,
        String type,
        String occurredAt,
        String dept,
        UUID assigneeId,
        UUID reservationId) {
}
