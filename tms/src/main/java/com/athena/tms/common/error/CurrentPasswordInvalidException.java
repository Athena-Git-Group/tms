package com.athena.tms.common.error;

/**
 * 本人設定新密碼（contracts changeOwnPassword）時，帶來的目前密碼跟帳號現在的密碼對不上：
 * 回 401 current_password_invalid，提示「目前密碼不正確」。
 *
 * <p>只用在自願變更密碼的情境；強制改密情境不比對目前密碼，不會走到這裡。
 */
public class CurrentPasswordInvalidException extends ApiException {
    public CurrentPasswordInvalidException(String message) {
        super(401, "current_password_invalid", message);
    }
}
