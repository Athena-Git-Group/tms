package com.athena.tms.dto;

/**
 * 櫃檯判讀的三選一請求體：
 * {"action":"assign","dept":"housekeeping|engineering"} 或
 * {"action":"answer","note":"..."}。
 * 「給房務／給工務」是同一個 assign 動作帶不同 dept，不是三個 action。
 */
public record TriageDecisionRequest(String action, String dept, String note) {
}
