package com.athena.tms.bdd.support;

import java.util.UUID;

/**
 * 推播（outbox kind=push）payload 的解析形狀，對應 contracts
 * {@code components/schemas/shared.yaml} 的 {@code PushPayload}／{@code PushTarget}。
 * 欄位名稱沿用契約裡的 JSON 命名（camelCase），讓 ObjectMapper 能直接按名稱
 * 對應到 record 元件建構，不用另外寫欄位對應的轉接程式碼。
 */
public record PushPayloadView(
        UUID recipientStaffId,
        String event,
        String title,
        String body,
        Target target) {

    /**
     * 哪些欄位必填由 {@code kind} 決定（task 要 taskId；pool 要 dept，
     * highlightTaskId 選填；home 兩者皆空），所以這裡一律宣告成可為 null，
     * 不用三種不同形狀的子型別表示。
     */
    public record Target(String kind, UUID taskId, String dept, UUID highlightTaskId) {
    }
}
