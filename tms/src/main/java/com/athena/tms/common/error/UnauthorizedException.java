package com.athena.tms.common.error;

/** 401：憑證缺失、過期或已撤銷（員工 JWT 無效、住客工作階段驗證失敗）。 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(401, "unauthorized", message);
    }
}
