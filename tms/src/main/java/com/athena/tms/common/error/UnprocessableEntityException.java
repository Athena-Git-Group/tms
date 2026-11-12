package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiException;
import java.util.Map;

public class UnprocessableEntityException
extends ApiException {
    public static final String DEFAULT_CODE = "validation_failed";

    public UnprocessableEntityException(String message) {
        this(DEFAULT_CODE, message);
    }

    public UnprocessableEntityException(String code, String message) {
        this(code, message, null);
    }

    /** context 對應 contracts Error.context，讓呼叫端拿到修正請求所需的資料，例如查房項目集合不符時的現行集合。 */
    public UnprocessableEntityException(String code, String message, Map<String, Object> context) {
        super(422, code, message, context);
    }
}
