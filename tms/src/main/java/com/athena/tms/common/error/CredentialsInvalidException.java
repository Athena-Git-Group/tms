package com.athena.tms.common.error;

/**
 * 員工登入（contracts staffAccountsLogin）密碼錯誤或員工編號不存在共用的例外：
 * 兩種原因回應同一句文案「員工編號或密碼不正確」，不洩漏員工編號是否存在。
 */
public class CredentialsInvalidException extends ApiException {
    public CredentialsInvalidException(String message) {
        super(401, "credentials_invalid", message);
    }
}
