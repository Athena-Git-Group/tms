package com.athena.tms.common.error;

/**
 * 員工帳號已停用（{@code staff_accounts.active=false}）時登入被拒：即使密碼正確也不放行，
 * 歷史資料保留（contracts staffAccountsLogin 403 account_disabled）。
 */
public class AccountDisabledException extends ApiException {
    public AccountDisabledException(String message) {
        super(403, "account_disabled", message);
    }
}
