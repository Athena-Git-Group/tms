package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiException;

public class StateConflictException
extends ApiException {
    public static final String DEFAULT_CODE = "state_conflict";

    public StateConflictException(String message) {
        this(DEFAULT_CODE, message);
    }

    public StateConflictException(String code, String message) {
        this(code, message, null);
    }

    public StateConflictException(String code, String message, java.util.Map<String, Object> context) {
        super(409, code, message, context);
    }
}
