package com.athena.tms.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 通知中心單筆通知的回應形狀，欄位對應 contracts 通知中心.yaml 的
 * notifications schema：id、title、body、target、createdAt、readAt。
 * 不帶 tenantId、recipientStaffId——這兩個欄位只是後端用來定收件範圍，
 * 不是呈現給呼叫者的資訊。
 */
public record NotificationResponse(
        UUID id, String title, String body, JsonNode target, OffsetDateTime createdAt, OffsetDateTime readAt) {}
