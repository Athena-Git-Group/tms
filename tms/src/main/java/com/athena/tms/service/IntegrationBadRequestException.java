package com.athena.tms.service;

import com.athena.tms.common.error.ApiException;

/**
 * 400：整合端點的查詢參數不合法（after 非十進位或負數、limit 越界）。
 * 既有錯誤體系沒有 400 的子類（內容驗證錯誤走 422），但整合契約對
 * 參數錯誤明定 400，所以在整合層自備一個 ApiException 子類，
 * 回應格式仍由 GlobalExceptionHandler 統一組裝。
 */
public class IntegrationBadRequestException extends ApiException {

    public IntegrationBadRequestException(String message) {
        super(400, "bad_request", message);
    }
}
