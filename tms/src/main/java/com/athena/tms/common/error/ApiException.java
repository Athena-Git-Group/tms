package com.athena.tms.common.error;

/**
 * 業務例外基底：HTTP 狀態碼＋機器可讀 code（openapi Error schema 的 code 欄位）。
 * 子類別一類狀態碼一個，GlobalExceptionHandler 統一映射成 ApiError 回應。
 */
public abstract class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    protected ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
