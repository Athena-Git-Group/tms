package com.athena.tms.common.error;

/** 422：內容驗證失敗（評分不在 1–5、查房 items 缺項等）。 */
public class UnprocessableEntityException extends ApiException {

    public static final String DEFAULT_CODE = "validation_failed";

    public UnprocessableEntityException(String message) {
        this(DEFAULT_CODE, message);
    }

    public UnprocessableEntityException(String code, String message) {
        super(422, code, message);
    }
}
