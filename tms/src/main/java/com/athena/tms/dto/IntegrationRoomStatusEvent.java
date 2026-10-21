package com.athena.tms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 給 PMS 的房況事件（wire 契約 room_status.v1）。欄位集合是封閉的：
 * 加欄位算向後相容、不升版；改語意、刪欄位或改型別就必須升 room_status.v2，
 * PMS 以 schema 欄位分流。欄位名用 snake_case 是對外契約的一部分，
 * 與專案內部 DTO 的 camelCase 慣例刻意不同，所以逐欄標 JsonProperty。
 *
 * <p>傳遞語意是 at-least-once：同一筆事件可能重複送達（拉取與 SSE 皆然），
 * PMS 端以 (task_id, seq) 去重。tenant_id 不得出現在 payload——租戶身分
 * 由 API key 決定，寫進 payload 等於多一處可能與實際授權漂移的租戶來源。
 */
public record IntegrationRoomStatusEvent(
        @JsonProperty("schema") String schema,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("room_no") String roomNo,
        @JsonProperty("hk_state") String hkState,
        @JsonProperty("task_id") UUID taskId,
        @JsonProperty("seq") int seq,
        @JsonProperty("occurred_at") OffsetDateTime occurredAt) {

    /** v1 的 schema 識別字，PMS 依此分流解析器。 */
    public static final String SCHEMA_V1 = "room_status.v1";
}
