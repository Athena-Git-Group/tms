package com.athena.tms.push;

import java.util.UUID;

/**
 * 一筆要送到裝置的推播，內容對應 contracts shared.yaml 的 PushPayload，外加 outbox 的冪等鍵。
 * {@code targetJson} 保留 PushTarget 的原始 JSON 字串，App 拿到後自己解析，後端不改形狀。
 */
public record PushMessage(
        String idempotencyKey,
        UUID recipientStaffId,
        String event,
        String title,
        String body,
        String targetJson) {
}
