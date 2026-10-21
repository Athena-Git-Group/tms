package com.athena.tms.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 建單指令（狀態機三個合法起點，spec §5.1）：
 * 選單→pool、自由文字→pool/triage、排房發佈→accepted（必帶 assignee）。
 * 值域驗證在 {@link TaskEngine#create}：guest 來源必帶訂單、cleaning 必帶
 * work_date（「一房一天僅一筆未完成打掃單」約束的判定依據）、accepted 起點 ⟺ 帶 assignee。
 */
public record TaskCreateCommand(
        long tenantId,
        String type,
        String source,
        UUID roomId,
        UUID reservationId,
        String dept,
        String initialState,
        UUID assigneeId,
        String title,
        String note,
        Integer estMin,
        UUID planId,
        LocalDate workDate,
        Actor actor) {
}
