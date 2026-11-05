package com.athena.tms.common.error;

import java.util.Map;

/**
 * 連續輸入錯誤密碼達 5 次鎖定帳號 15 分鐘，鎖定期間即使密碼正確也登入失敗
 * （contracts staffAccountsLogin 429 account_locked）。context 帶 lockedUntil／
 * remainingMinutes，讓前端能顯示「還要等多久」。
 */
public class AccountLockedException extends ApiException {
    public AccountLockedException(String message, Map<String, Object> context) {
        super(429, "account_locked", message, context);
    }
}
