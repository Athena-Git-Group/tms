package com.athena.tms.kb;

import java.util.List;
import java.util.UUID;

/**
 * 查一下的一次回答（contracts 的 KbAnswer）。answerId 就是這次提問的稽核紀錄
 * （kb_audit_logs）id，之後的回饋與轉問主管都拿它當位址。查不到時 found=false，
 * answer 為 null、sources 為空，只有 notFoundMessage 有文字。
 */
public record KbAnswer(
        UUID answerId, boolean found, String answer, String notFoundMessage, List<KbSource> sources) {
}
