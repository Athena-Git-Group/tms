package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiException;

/** 查詢參數本身不合法（值域外、範圍顛倒或過大），回 400，形狀與其他錯誤相同。 */
public class BadRequestException
extends ApiException {
    public static final String DEFAULT_CODE = "validation_failed";

    public BadRequestException(String message) {
        super(400, DEFAULT_CODE, message);
    }
}
