package com.athena.tms.common.error;

/** 429：限流（同房 10 分鐘內第 4 筆需求、掃碼 IP 失敗計數超門檻）。 */
public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String message) {
        super(429, "rate_limited", message);
    }
}
