package com.athena.tms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 事件拉取端點的回應：本頁事件陣列加上下一次拉取要帶的 next_cursor。
 * next_cursor 對 PMS 是不透明字串，規則：有事件時是本頁最後一筆的
 * event_id；空頁時原封不動回呼叫方帶來的 after；連 after 都沒帶的空頁
 * 回 "0"。空頁回原值而不是回空，讓 PMS 的輪詢迴圈永遠可以拿回應裡的
 * next_cursor 當下一輪的 after，不必為空頁寫特例。
 */
public record IntegrationEventsResponse(
        @JsonProperty("events") List<IntegrationRoomStatusEvent> events,
        @JsonProperty("next_cursor") String nextCursor) {
}
