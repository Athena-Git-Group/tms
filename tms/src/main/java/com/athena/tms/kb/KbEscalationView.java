package com.athena.tms.kb;

import java.util.UUID;

/**
 * 「問主管」提問的回應形狀，對應 contracts 的 KbEscalation。App 用它顯示
 * 提問進度，主管清單用它列出待回覆的提問。
 */
public record KbEscalationView(
        UUID id,
        UUID answerId,
        String question,
        UUID taskId,
        UUID askedBy,
        String status,
        String reply,
        UUID repliedBy) {
}
