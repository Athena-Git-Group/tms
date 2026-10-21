package com.athena.tms.dto;

/**
 * SOP 勾記的請求體：{"checked":true} 或 {"skipped":true}，恰取其一
 * （互斥驗證在 service；DB CHECK chk_task_sop_checks_exclusive 是最後防線）。
 * 用 Boolean 而非 boolean：缺欄位（null）與明寫 false 要分得出來，
 * 兩者都不算「選了這個動作」。
 */
public record SopCheckRequest(Boolean checked, Boolean skipped) {
}
