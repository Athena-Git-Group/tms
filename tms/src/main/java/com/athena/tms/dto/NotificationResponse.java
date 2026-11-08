package com.athena.tms.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 通知中心單筆通知的回應形狀，欄位對應 contracts 通知中心.yaml 的
 * notifications schema：id、event、title、body、target、createdAt、readAt。
 * 不帶 tenantId、recipientStaffId——這兩個欄位只是後端用來定收件範圍，
 * 不是呈現給呼叫者的資訊。
 *
 * <p>{@code event} 是這筆通知當初從哪一個推播事件落地的，值域照
 * contracts shared.yaml 的 PushPayload.event；通知中心用它決定每一列的
 * 分類圖示與顏色，少了它整份清單只能畫成同一種樣子。
 */
public record NotificationResponse(
        UUID id,
        String event,
        String title,
        String body,
        JsonNode target,
        OffsetDateTime createdAt,
        OffsetDateTime readAt) {}
